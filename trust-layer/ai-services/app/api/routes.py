import numpy as np
from fastapi import APIRouter, File, Form, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from typing import List

from app.models.ocr import verify_ocr
from app.models.face import verify_face, bytes_to_image
from app.models.liveness import verify_liveness
from app.models.deepfake import detect_deepfake
from app.models.fraud import compute_fraud_score
from app.models.transaction_fraud import score_transaction as _score_transaction

router = APIRouter()

import numpy as np

def _to_python(obj):
    """
    Recursively convert numpy types to plain Python types
    so JSONResponse can serialize them.
    """
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
# -------------------------------------------------------
# Shared helper
# -------------------------------------------------------
async def _read_image(upload: UploadFile) -> np.ndarray:
    """Read an UploadFile and decode it to an OpenCV numpy image."""
    if not upload.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail=f"{upload.filename} must be an image file, got: {upload.content_type}"
        )
    file_bytes = await upload.read()
    try:
        return bytes_to_image(file_bytes)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


# -------------------------------------------------------
# GET /health
# BE pings this to check if AI service is alive
# -------------------------------------------------------
@router.get("/health")
async def health():
    return {
        "status":  "ok",
        "service": "Kifiya eKYC AI Service"
    }


# -------------------------------------------------------
# POST /verify-id
#
# BE sends:
#   - id_image    : image file (multipart)
#   - full_name   : str (form field)
#   - dob         : str (form field) — EC date e.g. 13/06/1996
#   - id_number   : str (form field)
#   - gender      : str (form field)
#   - expiry_date : str (form field) — GC date e.g. 2026/06/20
#
# AI returns:
#   - extracted_fields : what OCR found on the ID
#   - match_scores     : per-field fuzzy match % vs user input
#   - ocr_confidence   : float 0.0–1.0  → BE stores in ai_verification
#   - overall_status   : verified | manual_review | rejected
#   - passed           : bool
# -------------------------------------------------------
@router.post("/verify-id")
async def verify_id(
    id_image:    UploadFile = File(..., description="Front of ID card"),
    full_name:   str        = Form(...),
    dob:         str        = Form(...),
    id_number:   str        = Form(...),
    gender:      str        = Form(...),
    expiry_date: str        = Form(...),
):
    img = await _read_image(id_image)

    user_input = {
        "full_name":   full_name.strip(),
        "dob":         dob.strip(),
        "id_number":   id_number.strip(),
        "gender":      gender.strip(),
        "expiry_date": expiry_date.strip(),
    }

    try:
        result = verify_ocr(img, user_input)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"OCR processing failed: {e}")

    return JSONResponse(content={
        "status":           "success",
        "extracted_fields": result["extracted_fields"],
        "match_scores":     result["match_scores"],
        "ocr_confidence":   result["ocr_confidence"],
        "overall_status":   result["overall_status"],
        "passed":           result["passed"],
    })


# -------------------------------------------------------
# POST /verify-face
#
# BE sends:
#   - id_image     : image file (multipart) — ID card front
#   - selfie_image : image file (multipart) — user selfie
#
# AI returns:
#   - similarity_pct    : float 0–100
#   - verified          : bool
#   - distance          : float raw cosine distance
#   - passed            : bool (our threshold, not DeepFace default)
#   - selfie_embedding  : list of 512 floats → BE stores in pgvector
#   - embedding_model   : str → BE stores in biometric_templates
#   - deepfake_score    : float 0.0–1.0 → BE stores in ai_verification
#   - deepfake_passed   : bool
#   - deepfake_signals  : dict (individual signal scores for audit)
# -------------------------------------------------------
@router.post("/verify-face")
async def verify_face_endpoint(
    id_image:     UploadFile = File(..., description="Front of ID card"),
    selfie_image: UploadFile = File(..., description="User selfie photo"),
):
    id_img     = await _read_image(id_image)
    selfie_img = await _read_image(selfie_image)

    # --- Face match ---
    try:
        face_result = verify_face(id_img, selfie_img)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Face verification failed: {e}")

    # --- Deepfake check on selfie ---
    # Failure here should not block the whole request — return neutral score
    try:
        deepfake_result = detect_deepfake(selfie_img)
    except Exception as e:
        deepfake_result = {
            "deepfake_score": 0.0,
            "is_deepfake":    False,
            "passed":         True,
            "signals":        {"error": str(e)},
        }

    return JSONResponse(content={
        "status":           "success",
        "similarity_pct":   face_result["similarity_pct"],
        "verified":         face_result["verified"],
        "distance":         face_result["distance"],
        "passed":           face_result["passed"],
        "selfie_embedding": face_result["selfie_embedding"],   # 512 floats for pgvector
        "embedding_model":  face_result["embedding_model"],
        "deepfake_score":   deepfake_result["deepfake_score"],
        "deepfake_passed":  deepfake_result["passed"],
        "deepfake_signals": deepfake_result["signals"],
    })


# -------------------------------------------------------
# POST /verify-liveness
#
# BE sends:
#   - frames : list of image files (multipart, 5–15 frames)
#              captured from webcam during active liveness check
#
# AI returns:
#   - blink_detected          : bool
#   - blink_count             : int
#   - head_movement_detected  : bool
#   - max_shift_pixels        : int
#   - liveness_score          : float 0.0–1.0 → BE stores in ai_verification
#   - liveness_passed         : bool
#   - frames_received         : int
#   - frames_with_face        : int
# -------------------------------------------------------
@router.post("/verify-liveness")
async def verify_liveness_endpoint(
    frames: List[UploadFile] = File(..., description="Webcam frames (5–15 images)"),
):
    if len(frames) == 0:
        raise HTTPException(status_code=400, detail="No frames received")

    frames_bytes = []
    for f in frames:
        if not f.content_type.startswith("image/"):
            raise HTTPException(
                status_code=400,
                detail=f"All frames must be image files, got: {f.content_type}"
            )
        frames_bytes.append(await f.read())

    try:
        result = verify_liveness(frames_bytes)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Liveness check failed: {e}")

    return JSONResponse(content=_to_python({      # ← wrap with _to_python
        "status":                  "success",
        "blink_detected":          result["blink_detected"],
        "blink_count":             result["blink_count"],
        "head_movement_detected":  result["head_movement_detected"],
        "max_shift_pixels":        result["max_shift_pixels"],
        "liveness_score":          result["liveness_score"],
        "liveness_passed":         result["liveness_passed"],
        "frames_received":         result["frames_received"],
        "frames_with_face":        result["frames_with_face"],
    }))


# -------------------------------------------------------
# POST /fraud-score
#
# BE sends JSON body — no files, pure scores from previous endpoints:
#   - ocr_confidence  : float 0.0–1.0  (from /verify-id)
#   - similarity_pct  : float 0–100    (from /verify-face)
#   - liveness_score  : float 0.0–1.0  (from /verify-liveness)
#   - liveness_passed : bool            (from /verify-liveness)
#   - deepfake_score  : float 0.0–1.0  (from /verify-face)
#
# AI returns:
#   - fraud_score     : float 0.0–1.0 → BE stores in ai_verification
#   - risk_level      : low | medium | high
#   - recommendation  : approve | review | reject
#   - passed          : bool
#   - hard_rejection  : str | null
#   - risk_signals    : dict
# -------------------------------------------------------
class FraudScoreRequest(BaseModel):
    ocr_confidence:  float = Field(..., ge=0.0, le=1.0)
    similarity_pct:  float = Field(..., ge=0.0, le=100.0)
    liveness_score:  float = Field(..., ge=0.0, le=1.0)
    liveness_passed: bool
    deepfake_score:  float = Field(..., ge=0.0, le=1.0)


@router.post("/fraud-score")
async def fraud_score(request: FraudScoreRequest):
    try:
        result = compute_fraud_score(
            ocr_confidence  = request.ocr_confidence,
            similarity_pct  = request.similarity_pct,
            liveness_score  = request.liveness_score,
            liveness_passed = request.liveness_passed,
            deepfake_score  = request.deepfake_score,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Fraud scoring failed: {e}")

    return JSONResponse(content={
        "status":         "success",
        "fraud_score":    result["fraud_score"],
        "risk_level":     result["risk_level"],
        "recommendation": result["recommendation"],
        "passed":         result["passed"],
        "hard_rejection": result["hard_rejection"],
        "risk_signals":   result["risk_signals"],
    })


# -------------------------------------------------------
# POST /score-transaction
#
# BE sends JSON body with transaction + user history features.
# BE must compute history features from DB before calling this.
#
# Layer 1: Rule engine  — hard blocks, instant
# Layer 2: XGBoost      — ML fraud probability + explainability
#
# AI returns:
#   - decision          : silent_approve | step_up | block
#   - fraud_probability : float → stored in transactions.risk_score
#   - risk_level        : low | medium | high
#   - layer_triggered   : rule_engine | ml_model
#   - rule_fired        : str | null (which rule fired if Layer 1)
#   - rule_reason       : str | null
#   - top_factors       : list → stored in fraud_logs.factors
#   - model_version     : str → stored in fraud_logs.model_version
# -------------------------------------------------------
class ScoreTransactionRequest(BaseModel):
    # Transaction fields
    amount:                  float = Field(..., gt=0)
    hour_of_day:             int   = Field(..., ge=0, le=23)
    day_of_week:             int   = Field(..., ge=0, le=6)
    location_country:        str   = Field(..., min_length=2, max_length=2)
    merchant_category_code:  str   = Field(..., min_length=4, max_length=4)
    auth_method:             str   = Field(..., description="silent | pin | biometric_push | offline_signed")

    # Card / account context (from BE)
    daily_limit_etb:         float = Field(..., gt=0)
    daily_spend_so_far:      float = Field(..., ge=0)
    account_locked:          bool

    # User history features — BE computes these from DB before calling AI
    is_new_device:           bool
    is_international:        bool
    merchant_risk:           float = Field(..., ge=0.0, le=1.0)
    transaction_count_1hr:   int   = Field(..., ge=0)
    transaction_count_24hr:  int   = Field(..., ge=0)
    days_since_registration: int   = Field(..., ge=0)
    amount_vs_avg_ratio:     float = Field(..., ge=0)
    failed_attempts_today:   int   = Field(..., ge=0)


@router.post("/score-transaction")
async def score_transaction(request: ScoreTransactionRequest):
    try:
        result = _score_transaction(
            amount                  = request.amount,
            hour_of_day             = request.hour_of_day,
            day_of_week             = request.day_of_week,
            location_country        = request.location_country,
            merchant_category_code  = request.merchant_category_code,
            auth_method             = request.auth_method,
            daily_limit_etb         = request.daily_limit_etb,
            daily_spend_so_far      = request.daily_spend_so_far,
            account_locked          = request.account_locked,
            is_new_device           = request.is_new_device,
            is_international        = request.is_international,
            merchant_risk           = request.merchant_risk,
            transaction_count_1hr   = request.transaction_count_1hr,
            transaction_count_24hr  = request.transaction_count_24hr,
            days_since_registration = request.days_since_registration,
            amount_vs_avg_ratio     = request.amount_vs_avg_ratio,
            failed_attempts_today   = request.failed_attempts_today,
        )
    except RuntimeError as e:
        # Model not loaded — train script not run yet
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Transaction scoring failed: {e}")

    return JSONResponse(content={
        "status":            "success",
        "decision":          result["decision"],
        "fraud_probability": result["fraud_probability"],
        "risk_level":        result["risk_level"],
        "layer_triggered":   result["layer_triggered"],
        "rule_fired":        result["rule_fired"],
        "rule_reason":       result["rule_reason"],
        "top_factors":       result["top_factors"],
        "model_version":     result["model_version"],
    })