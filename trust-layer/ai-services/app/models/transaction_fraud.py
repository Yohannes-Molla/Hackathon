"""
models/transaction_fraud.py

Transaction fraud detection — two layers:
    Layer 1: Rule engine   — hard blocks, instant, no ML
    Layer 2: XGBoost model — fraud probability score with explainability

Called by POST /api/v1/score-transaction
BE sends transaction features, AI returns decision + explanation.
"""

import os
import json
import numpy as np
import xgboost as xgb
from dataclasses import dataclass

# -----------------------------
# Load model once at module level
# Not on every request — model loading is expensive
# -----------------------------
_MODEL_PATH = os.path.join(
    os.path.dirname(__file__), "weights", "transaction_fraud_model.json"
)
_META_PATH = os.path.join(
    os.path.dirname(__file__), "weights", "transaction_fraud_model_meta.json"
)

_model: xgb.XGBClassifier | None = None
_feature_cols: list[str] = []
_model_version: str = "not_loaded"


def _load_model():
    global _model, _feature_cols, _model_version
    if _model is not None:
        return  # already loaded

    if not os.path.exists(_MODEL_PATH):
        raise RuntimeError(
            f"Transaction fraud model not found at {_MODEL_PATH}. "
            f"Run train_transaction_fraud_model.py first."
        )

    _model = xgb.XGBClassifier()
    _model.load_model(_MODEL_PATH)

    with open(_META_PATH) as f:
        meta = json.load(f)

    _feature_cols   = meta["feature_cols"]
    _model_version  = meta["model_version"]
    print(f"✅ Transaction fraud model loaded: {_model_version} "
          f"(ROC-AUC: {meta['roc_auc']})")


# Load at import time
_load_model()


# -------------------------------------------------------
# Auth method encoder
# Converts string to int for the model
# -------------------------------------------------------
_AUTH_METHOD_MAP = {
    "silent":          0,
    "pin":             1,
    "biometric_push":  2,
    "offline_signed":  1,   # treat same as pin
}


# -------------------------------------------------------
# Layer 1 — Rule Engine
# Hard rules that block or flag BEFORE ML runs.
# Returns a decision string or None if no rule fires.
# -------------------------------------------------------

# Sanctioned/high-risk countries (simplified list for hackathon)
_SANCTIONED_COUNTRIES = {"KP", "IR", "SY", "CU", "VE"}

# High-risk merchant category codes
_HIGH_RISK_MCC = {
    "6051",  # crypto / non-fiat currency
    "7995",  # gambling / betting
    "6538",  # money transfer
    "4829",  # wire transfer
    "6099",  # financial services
}


@dataclass
class RuleResult:
    fired:       bool
    rule_name:   str | None
    decision:    str | None   # block | step_up | None
    reason:      str | None


def run_rule_engine(
    amount:              float,
    daily_limit_etb:     float,
    daily_spend_so_far:  float,
    location_country:    str,
    merchant_category_code: str,
    transaction_count_1hr:  int,
    account_locked:      bool,
    is_new_device:       bool,
    amount_vs_avg_ratio: float,
) -> RuleResult:
    """
    Layer 1: evaluate hard rules in priority order.
    First rule that fires wins — no further rules checked.

    Returns RuleResult with fired=True if a rule fired,
    fired=False if all rules passed (proceed to ML).
    """

    # Rule 1 — Account locked (highest priority)
    if account_locked:
        return RuleResult(
            fired=True, rule_name="account_locked",
            decision="block",
            reason="Account is locked — no transactions permitted"
        )

    # Rule 2 — Sanctioned country
    if location_country.upper() in _SANCTIONED_COUNTRIES:
        return RuleResult(
            fired=True, rule_name="sanctioned_country",
            decision="block",
            reason=f"Transaction origin country {location_country} is sanctioned"
        )

    # Rule 3 — Daily limit exceeded
    if daily_spend_so_far + amount > daily_limit_etb:
        return RuleResult(
            fired=True, rule_name="daily_limit_exceeded",
            decision="block",
            reason=f"Transaction would exceed daily limit of {daily_limit_etb} ETB"
        )

    # Rule 4 — Extreme velocity (>10 transactions in last hour)
    if transaction_count_1hr > 10:
        return RuleResult(
            fired=True, rule_name="velocity_extreme",
            decision="block",
            reason=f"Extreme velocity: {transaction_count_1hr} transactions in last hour"
        )

    # Rule 5 — High risk MCC — always step up
    if merchant_category_code in _HIGH_RISK_MCC:
        return RuleResult(
            fired=True, rule_name="high_risk_merchant",
            decision="step_up",
            reason=f"High-risk merchant category: MCC {merchant_category_code}"
        )

    # Rule 6 — New device + large amount — step up
    if is_new_device and amount > 5000:
        return RuleResult(
            fired=True, rule_name="new_device_large_amount",
            decision="step_up",
            reason="Large transaction from unrecognized device requires approval"
        )

    # Rule 7 — Amount far above user's average — step up
    if amount_vs_avg_ratio > 10:
        return RuleResult(
            fired=True, rule_name="unusual_amount",
            decision="step_up",
            reason=f"Amount is {amount_vs_avg_ratio:.1f}x higher than user average"
        )

    # No rule fired — proceed to ML
    return RuleResult(fired=False, rule_name=None, decision=None, reason=None)


# -------------------------------------------------------
# Layer 2 — XGBoost fraud scoring
# -------------------------------------------------------

def _build_feature_vector(features: dict) -> np.ndarray:
    """
    Build feature array in the exact order the model was trained on.
    Order matters — model trained on specific column order.
    """
    return np.array([[features[col] for col in _feature_cols]])


def _get_top_factors(features: dict, n: int = 3) -> list[dict]:
    """
    Return top N features contributing to this fraud score.
    Uses model's feature importances weighted by feature value deviation.
    Stored in fraud_logs.factors JSONB on BE side.
    """
    importances = dict(zip(_feature_cols, _model.feature_importances_))

    # Weight importance by how "extreme" the feature value is
    # e.g. transaction_count_1hr=15 is more suspicious than =1
    # Normalized to 0–1 range based on typical ranges
    typical_ranges = {
        "amount":                    (10, 50000),
        "hour_of_day":               (0, 23),
        "day_of_week":               (0, 6),
        "is_international":          (0, 1),
        "is_new_device":             (0, 1),
        "merchant_risk":             (0, 1),
        "transaction_count_1hr":     (0, 20),
        "transaction_count_24hr":    (0, 60),
        "days_since_registration":   (0, 1000),
        "amount_vs_avg_ratio":       (0.1, 30),
        "failed_attempts_today":     (0, 5),
        "auth_method_encoded":       (0, 2),
    }

    weighted_scores = {}
    for col in _feature_cols:
        val = features[col]
        lo, hi = typical_ranges.get(col, (0, 1))
        normalized = (val - lo) / (hi - lo + 1e-9)
        normalized = max(0.0, min(1.0, normalized))
        weighted_scores[col] = importances[col] * normalized

    # Return top N as list of dicts (matching schema fraud_logs.factors format)
    top = sorted(weighted_scores.items(), key=lambda x: x[1], reverse=True)[:n]
    return [
        {"type": feat, "weight": round(score, 4)}
        for feat, score in top
    ]


def run_ml_scoring(features: dict) -> dict:
    """
    Layer 2: run XGBoost model on transaction features.

    Returns:
        fraud_probability : float 0.0–1.0
        decision          : silent_approve | step_up | block
        top_factors       : list of {type, weight} — explainability
        model_version     : str
    """
    feature_vector  = _build_feature_vector(features)
    fraud_proba     = float(_model.predict_proba(feature_vector)[0][1])
    fraud_proba     = round(fraud_proba, 4)

    # Decision thresholds
    if fraud_proba >= 0.80:
        decision = "block"
    elif fraud_proba >= 0.50:
        decision = "step_up"
    else:
        decision = "silent_approve"

    top_factors = _get_top_factors(features, n=3)

    return {
        "fraud_probability": fraud_proba,
        "decision":          decision,
        "top_factors":       top_factors,
        "model_version":     _model_version,
    }


# -------------------------------------------------------
# Main scoring function
# Called by /score-transaction endpoint
# -------------------------------------------------------
def score_transaction(
    # Transaction fields (from transactions table)
    amount:                  float,
    hour_of_day:             int,
    day_of_week:             int,
    location_country:        str,
    merchant_category_code:  str,
    auth_method:             str,

    # Card / account context (BE queries and sends)
    daily_limit_etb:         float,
    daily_spend_so_far:      float,
    account_locked:          bool,

    # User history features (BE computes from DB)
    is_new_device:           bool,
    is_international:        bool,
    merchant_risk:           float,
    transaction_count_1hr:   int,
    transaction_count_24hr:  int,
    days_since_registration: int,
    amount_vs_avg_ratio:     float,
    failed_attempts_today:   int,
) -> dict:
    """
    Full two-layer transaction fraud scoring.

    Layer 1: Rule engine — hard blocks first
    Layer 2: XGBoost — ML fraud probability

    Returns:
        {
            decision          : silent_approve | step_up | block
            fraud_probability : float (0.0–1.0)  → stored in transactions.risk_score
            risk_level        : low | medium | high
            layer_triggered   : rule_engine | ml_model
            rule_fired        : str | None  (which rule fired, if Layer 1)
            rule_reason       : str | None
            top_factors       : list        → stored in fraud_logs.factors
            model_version     : str         → stored in fraud_logs.model_version
        }
    """

    # --- Layer 1: Rule engine ---
    rule_result = run_rule_engine(
        amount               = amount,
        daily_limit_etb      = daily_limit_etb,
        daily_spend_so_far   = daily_spend_so_far,
        location_country     = location_country,
        merchant_category_code = merchant_category_code,
        transaction_count_1hr  = transaction_count_1hr,
        account_locked       = account_locked,
        is_new_device        = is_new_device,
        amount_vs_avg_ratio  = amount_vs_avg_ratio,
    )

    if rule_result.fired:
        # Rule fired — no need to run ML
        fraud_proba = 1.0 if rule_result.decision == "block" else 0.65
        return {
            "decision":          rule_result.decision,
            "fraud_probability": fraud_proba,
            "risk_level":        "high" if rule_result.decision == "block" else "medium",
            "layer_triggered":   "rule_engine",
            "rule_fired":        rule_result.rule_name,
            "rule_reason":       rule_result.reason,
            "top_factors":       [{"type": rule_result.rule_name, "weight": 1.0}],
            "model_version":     "rule_engine_v1",
        }

    # --- Layer 2: XGBoost ---
    features = {
        "amount":                  amount,
        "hour_of_day":             hour_of_day,
        "day_of_week":             day_of_week,
        "is_international":        int(is_international),
        "is_new_device":           int(is_new_device),
        "merchant_risk":           merchant_risk,
        "transaction_count_1hr":   transaction_count_1hr,
        "transaction_count_24hr":  transaction_count_24hr,
        "days_since_registration": days_since_registration,
        "amount_vs_avg_ratio":     amount_vs_avg_ratio,
        "failed_attempts_today":   failed_attempts_today,
        "auth_method_encoded":     _AUTH_METHOD_MAP.get(auth_method, 0),
    }

    ml_result = run_ml_scoring(features)

    # Risk level from ML probability
    if ml_result["fraud_probability"] >= 0.80:
        risk_level = "high"
    elif ml_result["fraud_probability"] >= 0.50:
        risk_level = "medium"
    else:
        risk_level = "low"

    return {
        "decision":          ml_result["decision"],
        "fraud_probability": ml_result["fraud_probability"],
        "risk_level":        risk_level,
        "layer_triggered":   "ml_model",
        "rule_fired":        None,
        "rule_reason":       None,
        "top_factors":       ml_result["top_factors"],
        "model_version":     ml_result["model_version"],
    }