"""
utils/serializer.py

Cleans and formats all AI endpoint responses into consistent JSON-safe dicts
before they are returned to the Backend (Node.js).

BE will receive these and save the relevant fields to:
    - ai_verification      (ocr_confidence, similarity_pct, liveness_score, etc.)
    - biometric_templates  (selfie_embedding, embedding_model)
    - transactions         (risk_score / fraud_probability)
    - fraud_logs           (top_factors, model_version)
"""

import numpy as np


# -----------------------------
# Internal helper
# Recursively converts all numpy types → plain Python types
# Prevents JSONResponse serialization errors
# -----------------------------
def _to_python(obj):
    if isinstance(obj, dict):
        return {k: _to_python(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [_to_python(v) for v in obj]
    elif isinstance(obj, np.integer):
        return int(obj)
    elif isinstance(obj, np.floating):
        return float(obj)
    elif isinstance(obj, np.bool_):
        return bool(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    return obj


# -----------------------------
# /verify-id
# BE stores: ocr_confidence → ai_verification.ocr_confidence
# -----------------------------
def serialize_ocr_response(result: dict) -> dict:
    """
    Serialize response from verify_ocr() for /verify-id endpoint.

    Fields BE saves to ai_verification:
        - ocr_confidence
        - overall_status
        - passed
    """
    return _to_python({
        "status":           "success",
        "extracted_fields": {
            "full_name":  result["extracted_fields"].get("full_name"),
            "dob_ec":     result["extracted_fields"].get("dob_ec"),
            "dob_gc":     result["extracted_fields"].get("dob_gc"),
            "id_number":  result["extracted_fields"].get("id_number"),
            "gender":     result["extracted_fields"].get("gender"),
            "expiry_gc":  result["extracted_fields"].get("expiry_gc"),
            "expiry_ec":  result["extracted_fields"].get("expiry_ec"),
        },
        "match_scores":     result["match_scores"],
        "ocr_confidence":   result["ocr_confidence"],   # → ai_verification
        "overall_status":   result["overall_status"],
        "passed":           result["passed"],
    })


# -----------------------------
# /verify-face
# BE stores:
#   - similarity_pct      → ai_verification.face_match_score
#   - selfie_embedding    → biometric_templates.face_embedding (pgvector)
#   - embedding_model     → biometric_templates.model_version
#   - deepfake_score      → ai_verification.deepfake_score
# -----------------------------
def serialize_face_response(face_result: dict, deepfake_result: dict) -> dict:
    """
    Serialize combined face match + deepfake result for /verify-face endpoint.

    Args:
        face_result     : from verify_face()
        deepfake_result : from detect_deepfake()
    """
    return _to_python({
        "status":           "success",
        "similarity_pct":   face_result["similarity_pct"],     # → ai_verification
        "verified":         face_result["verified"],
        "distance":         face_result["distance"],
        "passed":           face_result["passed"],
        "selfie_embedding": face_result["selfie_embedding"],   # → biometric_templates (pgvector)
        "embedding_model":  face_result["embedding_model"],    # → biometric_templates
        "deepfake_score":   deepfake_result["deepfake_score"], # → ai_verification
        "deepfake_passed":  deepfake_result["passed"],
        "deepfake_signals": deepfake_result["signals"],
    })


# -----------------------------
# /verify-liveness
# BE stores: liveness_score → ai_verification.liveness_score
# -----------------------------
def serialize_liveness_response(result: dict) -> dict:
    """
    Serialize response from verify_liveness() for /verify-liveness endpoint.

    Fields BE saves to ai_verification:
        - liveness_score
        - liveness_passed
    """
    return _to_python({
        "status":                  "success",
        "blink_detected":          result["blink_detected"],
        "blink_count":             result["blink_count"],
        "head_movement_detected":  result["head_movement_detected"],
        "max_shift_pixels":        result["max_shift_pixels"],
        "liveness_score":          result["liveness_score"],   # → ai_verification
        "liveness_passed":         result["liveness_passed"],
        "frames_received":         result["frames_received"],
        "frames_with_face":        result["frames_with_face"],
    })


# -----------------------------
# /fraud-score
# BE stores: fraud_score → ai_verification.fraud_score
# -----------------------------
def serialize_fraud_response(result: dict) -> dict:
    """
    Serialize response from compute_fraud_score() for /fraud-score endpoint.

    Fields BE saves to ai_verification:
        - fraud_score
        - risk_level
        - recommendation
        - passed
    """
    return _to_python({
        "status":         "success",
        "fraud_score":    result["fraud_score"],       # → ai_verification
        "risk_level":     result["risk_level"],
        "recommendation": result["recommendation"],
        "passed":         result["passed"],
        "hard_rejection": result["hard_rejection"],
        "risk_signals":   result["risk_signals"],
    })


# -----------------------------
# /score-transaction
# BE stores:
#   - fraud_probability → transactions.risk_score
#   - top_factors       → fraud_logs.factors (JSONB)
#   - model_version     → fraud_logs.model_version
# -----------------------------
def serialize_transaction_response(result: dict) -> dict:
    """
    Serialize response from score_transaction() for /score-transaction endpoint.

    Fields BE saves:
        - fraud_probability → transactions.risk_score
        - top_factors       → fraud_logs.factors
        - model_version     → fraud_logs.model_version
        - decision          → used by BE to decide next step
    """
    return _to_python({
        "status":            "success",
        "decision":          result["decision"],           # → BE routing logic
        "fraud_probability": result["fraud_probability"],  # → transactions.risk_score
        "risk_level":        result["risk_level"],
        "layer_triggered":   result["layer_triggered"],
        "rule_fired":        result["rule_fired"],
        "rule_reason":       result["rule_reason"],
        "top_factors":       result["top_factors"],        # → fraud_logs.factors
        "model_version":     result["model_version"],      # → fraud_logs.model_version
    })