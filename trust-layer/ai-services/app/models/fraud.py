from app.core.config import (
    FRAUD_WEIGHT_OCR,
    FRAUD_WEIGHT_FACE,
    FRAUD_WEIGHT_LIVENESS,
    FRAUD_WEIGHT_DEEPFAKE,
    FRAUD_REVIEW_THRESHOLD,
    FRAUD_REJECT_THRESHOLD,
    OCR_MIN_SCORE,
    FACE_MIN_SCORE,
    LIVENESS_MIN_SCORE,
    DEEPFAKE_MAX_SCORE,
)


# -----------------------------
# Convert individual scores → normalized risk signals (0.0–1.0)
# All inputs come from BE (which collected them from /verify-id,
# /verify-face, /verify-liveness responses)
# Higher output = higher risk
# -----------------------------

def _ocr_risk(ocr_confidence: float) -> float:
    """
    OCR confidence is already 0.0–1.0 where higher = more confident.
    Invert it: low OCR confidence = high risk.
    """
    return round(1.0 - min(max(ocr_confidence, 0.0), 1.0), 4)


def _face_risk(similarity_pct: float) -> float:
    """
    Face similarity is 0–100. Convert to 0.0–1.0 risk.
    Low similarity = high risk.
    """
    similarity_normalized = min(max(similarity_pct, 0.0), 100.0) / 100.0
    return round(1.0 - similarity_normalized, 4)


def _liveness_risk(liveness_score: float) -> float:
    """
    Liveness score is 0.0–1.0. Invert: low liveness = high risk.
    """
    return round(1.0 - min(max(liveness_score, 0.0), 1.0), 4)


def _deepfake_risk(deepfake_score: float) -> float:
    """
    Deepfake score is already a risk score (higher = more fake).
    Pass through directly.
    """
    return round(min(max(deepfake_score, 0.0), 1.0), 4)


# -----------------------------
# Hard rejection checks
# These bypass the weighted score — instant reject regardless of other scores
# -----------------------------
def _check_hard_rejections(
    ocr_confidence: float,
    similarity_pct: float,
    liveness_passed: bool,
    deepfake_score: float,
) -> str | None:
    """
    Returns a rejection reason string if any hard rule is triggered,
    otherwise returns None (no hard rejection).
    """
    if deepfake_score > DEEPFAKE_MAX_SCORE:
        return "deepfake_detected"

    if not liveness_passed:
        return "liveness_failed"

    if (similarity_pct / 100.0) < FACE_MIN_SCORE:
        return "face_mismatch"

    if ocr_confidence < (OCR_MIN_SCORE * 0.5):
        # Very low OCR — document is likely unreadable or fraudulent
        return "ocr_unreadable"

    return None


# -----------------------------
# Main fraud scoring function
# Called by /fraud-score endpoint
# BE sends all scores collected from the 3 previous endpoints
# -----------------------------
def compute_fraud_score(
    ocr_confidence:  float,
    similarity_pct:  float,
    liveness_score:  float,
    liveness_passed: bool,
    deepfake_score:  float,
) -> dict:
    """
    Combine all AI verification signals into a single fraud risk score.

    Args:
        ocr_confidence  : from /verify-id  — 0.0 to 1.0
        similarity_pct  : from /verify-face — 0.0 to 100.0
        liveness_score  : from /verify-liveness — 0.0 to 1.0
        liveness_passed : from /verify-liveness — bool
        deepfake_score  : from /verify-face (deepfake check) — 0.0 to 1.0

    Returns:
        {
            fraud_score     : float  (0.0–1.0, higher = more risky)
            risk_level      : str    (low | medium | high)
            recommendation  : str    (approve | review | reject)
            passed          : bool
            hard_rejection  : str | None  (reason if hard-rejected)
            risk_signals    : dict   (individual risk contributions)
        }
    """

    # --- Check hard rejection rules first ---
    hard_rejection = _check_hard_rejections(
        ocr_confidence  = ocr_confidence,
        similarity_pct  = similarity_pct,
        liveness_passed = liveness_passed,
        deepfake_score  = deepfake_score,
    )

    if hard_rejection:
        return {
            "fraud_score":    1.0,
            "risk_level":     "high",
            "recommendation": "reject",
            "passed":         False,
            "hard_rejection": hard_rejection,
            "risk_signals": {
                "ocr_risk":       _ocr_risk(ocr_confidence),
                "face_risk":      _face_risk(similarity_pct),
                "liveness_risk":  _liveness_risk(liveness_score),
                "deepfake_risk":  _deepfake_risk(deepfake_score),
            },
        }

    # --- Compute individual risk signals ---
    ocr_risk      = _ocr_risk(ocr_confidence)
    face_risk     = _face_risk(similarity_pct)
    liveness_risk = _liveness_risk(liveness_score)
    deepfake_risk = _deepfake_risk(deepfake_score)

    # --- Weighted fraud score ---
    fraud_score = round(
        ocr_risk      * FRAUD_WEIGHT_OCR      +
        face_risk     * FRAUD_WEIGHT_FACE     +
        liveness_risk * FRAUD_WEIGHT_LIVENESS +
        deepfake_risk * FRAUD_WEIGHT_DEEPFAKE,
        4
    )

    # --- Risk level and recommendation ---
    if fraud_score >= FRAUD_REJECT_THRESHOLD:
        risk_level     = "high"
        recommendation = "reject"
        passed         = False
    elif fraud_score >= FRAUD_REVIEW_THRESHOLD:
        risk_level     = "medium"
        recommendation = "review"
        passed         = False
    else:
        risk_level     = "low"
        recommendation = "approve"
        passed         = True

    return {
        "fraud_score":    fraud_score,
        "risk_level":     risk_level,
        "recommendation": recommendation,
        "passed":         passed,
        "hard_rejection": None,
        "risk_signals": {
            "ocr_risk":      ocr_risk,
            "face_risk":     face_risk,
            "liveness_risk": liveness_risk,
            "deepfake_risk": deepfake_risk,
        },
    }