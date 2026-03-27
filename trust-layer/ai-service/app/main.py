from __future__ import annotations

import io
import re
from typing import Any

import cv2
import numpy as np
import pytesseract
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image

app = FastAPI(
    title="Trust Layer AI Service",
    version="0.1.0",
    description="AI endpoints for OCR, liveness, anti-spoof, and risk scoring.",
)


def _load_image_bytes(raw: bytes) -> np.ndarray:
    if not raw:
        raise HTTPException(status_code=400, detail="Empty image payload")
    array = np.frombuffer(raw, dtype=np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Invalid image format")
    return image


def _normalize(value: float, min_v: float, max_v: float) -> float:
    if max_v <= min_v:
        return 0.0
    return max(0.0, min(1.0, (value - min_v) / (max_v - min_v)))


def _extract_document_fields(text: str) -> dict[str, str | None]:
    compact = " ".join(text.split())

    name_match = re.search(r"(?:name|full name)\s*[:\-]?\s*([A-Z][A-Z ]{2,60})", compact, re.IGNORECASE)
    dob_match = re.search(
        r"(?:dob|date of birth|birth)\s*[:\-]?\s*([0-3]?\d[\/\-][0-1]?\d[\/\-](?:19|20)\d{2})",
        compact,
        re.IGNORECASE,
    )
    doc_number_match = re.search(
        r"(?:id no|id number|document number|passport no|passport number)\s*[:\-]?\s*([A-Z0-9\-]{5,30})",
        compact,
        re.IGNORECASE,
    )
    nationality_match = re.search(r"(?:nationality)\s*[:\-]?\s*([A-Z][A-Za-z ]{2,40})", compact, re.IGNORECASE)

    return {
        "name": name_match.group(1).strip() if name_match else None,
        "dob": dob_match.group(1).strip() if dob_match else None,
        "document_number": doc_number_match.group(1).strip() if doc_number_match else None,
        "nationality": nationality_match.group(1).strip() if nationality_match else None,
    }


def _ocr_confidence(gray: np.ndarray) -> float:
    data = pytesseract.image_to_data(gray, output_type=pytesseract.Output.DICT)
    conf_values = []
    for item in data.get("conf", []):
        try:
            conf = float(item)
            if conf >= 0:
                conf_values.append(conf)
        except ValueError:
            continue
    if not conf_values:
        return 0.0
    return float(sum(conf_values) / len(conf_values)) / 100.0


def _frame_liveness_features(frame: np.ndarray) -> tuple[float, float, float]:
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    lap_var = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    brightness = float(np.mean(gray))
    edges = cv2.Canny(gray, 80, 160)
    edge_density = float(np.mean(edges > 0))
    return lap_var, brightness, edge_density


def _compute_liveness_score(frames: list[np.ndarray]) -> tuple[float, dict[str, float]]:
    if not frames:
        raise HTTPException(status_code=400, detail="At least one frame is required")

    lap_vars: list[float] = []
    brightness_values: list[float] = []
    edge_densities: list[float] = []
    means: list[float] = []

    for frame in frames:
        lap_var, brightness, edge_density = _frame_liveness_features(frame)
        lap_vars.append(lap_var)
        brightness_values.append(brightness)
        edge_densities.append(edge_density)
        means.append(float(np.mean(frame)))

    motion_std = float(np.std(means))
    sharpness = float(np.mean(lap_vars))
    brightness_var = float(np.std(brightness_values))
    edge_score = float(np.mean(edge_densities))

    sharpness_score = _normalize(sharpness, 1.0, 35.0)
    motion_score = _normalize(motion_std, 0.05, 5.0)
    brightness_score = _normalize(brightness_var, 0.05, 5.0)
    texture_score = _normalize(edge_score, 0.002, 0.08)

    score = (
        (0.45 * sharpness_score)
        + (0.10 * motion_score)
        + (0.15 * brightness_score)
        + (0.30 * texture_score)
    )
    return max(0.0, min(1.0, score)), {
        "sharpness": sharpness,
        "motion_std": motion_std,
        "brightness_variation": brightness_var,
        "edge_density": edge_score,
    }


def _compute_spoof_score(image: np.ndarray) -> tuple[float, dict[str, float]]:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    lap_var = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)

    saturation_var = float(np.var(hsv[:, :, 1]))
    value_mean = float(np.mean(hsv[:, :, 2]))

    highlights = float(np.mean(hsv[:, :, 2] > 245))
    local_contrast = float(np.std(gray))

    texture_score = _normalize(lap_var, 50.0, 800.0)
    saturation_score = _normalize(saturation_var, 15.0, 500.0)
    reflection_penalty = _normalize(highlights, 0.60, 0.90)
    contrast_score = _normalize(local_contrast, 8.0, 45.0)
    exposure_penalty = _normalize(abs(value_mean - 200.0), 0.0, 100.0)

    realness = (
        (0.35 * texture_score)
        + (0.25 * saturation_score)
        + (0.25 * contrast_score)
        - (0.10 * reflection_penalty)
        - (0.05 * exposure_penalty)
    )
    realness = max(0.0, min(1.0, realness))
    spoof_probability = 1.0 - realness

    return spoof_probability, {
        "laplacian_variance": lap_var,
        "saturation_variance": saturation_var,
        "highlight_ratio": highlights,
        "local_contrast": local_contrast,
        "value_mean": value_mean,
    }


def _risk_score(document_confidence: float, liveness_score: float, spoof_probability: float) -> dict[str, Any]:
    confidence_component = (1.0 - document_confidence) * 0.40
    liveness_component = (1.0 - liveness_score) * 0.35
    spoof_component = spoof_probability * 0.25

    risk = (confidence_component + liveness_component + spoof_component) * 100.0
    risk = max(0.0, min(100.0, risk))

    decision = "APPROVE"
    if risk >= 70:
        decision = "REJECT"
    elif risk >= 40:
        decision = "MANUAL_REVIEW"

    return {
        "risk_score": round(risk, 2),
        "decision": decision,
        "components": {
            "document_component": round(confidence_component * 100.0, 2),
            "liveness_component": round(liveness_component * 100.0, 2),
            "spoof_component": round(spoof_component * 100.0, 2),
        },
    }


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/ai/verify-document")
async def verify_document(document: UploadFile = File(...)) -> dict[str, Any]:
    raw = await document.read()
    image = _load_image_bytes(raw)
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    pil_image = Image.open(io.BytesIO(raw))
    text = pytesseract.image_to_string(pil_image)
    fields = _extract_document_fields(text)
    confidence = _ocr_confidence(gray)

    return {
        "status": "processed",
        "fields": fields,
        "confidence": round(confidence, 4),
        "method": "tesseract",
        "raw_text_excerpt": text[:500],
    }


@app.post("/api/ai/verify-liveness")
async def verify_liveness(frames: list[UploadFile] = File(...)) -> dict[str, Any]:
    decoded_frames: list[np.ndarray] = []
    for frame in frames:
        decoded_frames.append(_load_image_bytes(await frame.read()))

    score, diagnostics = _compute_liveness_score(decoded_frames)
    return {
        "status": "processed",
        "liveness_score": round(score, 4),
        "passed": score >= 0.40,
        "frame_count": len(decoded_frames),
        "diagnostics": diagnostics,
    }


@app.post("/api/ai/anti-spoof")
async def anti_spoof(image: UploadFile = File(...)) -> dict[str, Any]:
    frame = _load_image_bytes(await image.read())
    spoof_score, diagnostics = _compute_spoof_score(frame)
    return {
        "status": "processed",
        "spoof_probability": round(spoof_score, 4),
        "passed": spoof_score < 0.50,
        "diagnostics": diagnostics,
    }


@app.post("/api/ai/risk-score")
async def risk_score(
    document: UploadFile = File(...),
    liveness_frames: list[UploadFile] = File(...),
) -> dict[str, Any]:
    document_raw = await document.read()
    doc_img = _load_image_bytes(document_raw)
    doc_gray = cv2.cvtColor(doc_img, cv2.COLOR_BGR2GRAY)
    doc_confidence = _ocr_confidence(doc_gray)
    doc_text = pytesseract.image_to_string(Image.open(io.BytesIO(document_raw)))
    doc_fields = _extract_document_fields(doc_text)

    frames = [_load_image_bytes(await frame.read()) for frame in liveness_frames]
    liveness_score, liveness_diagnostics = _compute_liveness_score(frames)

    spoof_probability, spoof_diagnostics = _compute_spoof_score(doc_img)
    risk = _risk_score(doc_confidence, liveness_score, spoof_probability)

    return {
        "status": "processed",
        "document_confidence": round(doc_confidence, 4),
        "document_fields": doc_fields,
        "liveness_score": round(liveness_score, 4),
        "spoof_probability": round(spoof_probability, 4),
        "risk": risk,
        "diagnostics": {
            "liveness": liveness_diagnostics,
            "anti_spoof": spoof_diagnostics,
        },
    }
