import re
import cv2
import numpy as np
import pytesseract
from rapidfuzz import fuzz, process

from app.core.config import (
    TESSERACT_CMD,
    OCR_MIN_SCORE,
    NAME_MATCH_MIN_SCORE,
)

# Apply Tesseract path from config
pytesseract.pytesseract.tesseract_cmd = TESSERACT_CMD

# -----------------------------
# Date regex
# Matches: DD/MM/YYYY, YYYY/MM/DD, YYYY/Mon/DD (e.g. 2034/Feb/27)
# -----------------------------
DATE_PATTERN = re.compile(
    r'\b(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{4}'
    r'|\d{4}[\/\-]\d{1,2}[\/\-]\d{1,2}'
    r'|\d{4}[\/\-][A-Za-z]{3}[\/\-]\d{1,2})\b'
)


# -----------------------------
# Image preprocessing
# -----------------------------
def preprocess_image(img: np.ndarray) -> np.ndarray:
    """
    Upscale, denoise, and threshold the image for better OCR accuracy.
    Works well on Ethiopian Digital ID cards.
    """
    # Upscale — helps a lot for low-res or compressed ID scans
    img = cv2.resize(img, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    gray = cv2.fastNlMeansDenoising(gray, h=30)
    thresh = cv2.adaptiveThreshold(
        gray, 255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        blockSize=31,
        C=10
    )
    return thresh


# -----------------------------
# Helpers
# -----------------------------
def extract_dates_from(text: str) -> list[str]:
    return DATE_PATTERN.findall(text)


def find_label_line_index(lines: list[str], keywords: list[str]) -> int | None:
    """Return index of first line containing any of the keywords (case-insensitive)."""
    for i, line in enumerate(lines):
        if any(kw.lower() in line.lower() for kw in keywords):
            return i
    return None


def clean_ocr_noise(text: str) -> str:
    """
    Remove non-alphanumeric noise and strip short junk tokens from edges.
    e.g. "an _ Abdurezak Abazinab Abalencho sy" → "Abdurezak Abazinab Abalencho"
    """
    text = re.sub(r'[^A-Za-z0-9\s\/\-]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    tokens = text.split()

    def is_noise(tok: str) -> bool:
        return len(tok) <= 2 or not tok.isalpha()

    while tokens and is_noise(tokens[0]):
        tokens.pop(0)
    while tokens and is_noise(tokens[-1]):
        tokens.pop()

    return " ".join(tokens)


def compute_ocr_confidence(match_scores: dict) -> float:
    """
    Derive an overall OCR confidence score (0.0–1.0) from field match scores.
    Weighted: id_number and dob are more reliable than name.
    """
    weights = {
        "full_name": 0.30,
        "dob":       0.30,
        "id_number": 0.40,
    }
    total = 0.0
    for field, weight in weights.items():
        score = match_scores.get(field, 0.0)
        total += (score / 100.0) * weight
    return round(total, 4)


# -----------------------------
# Core extraction function
# -----------------------------
def extract_id_fields(img: np.ndarray) -> dict:
    """
    Run OCR on an ID card image and extract structured fields.

    Returns a dict with:
        raw_text       : full OCR output string
        lines          : cleaned list of OCR lines
        full_name      : extracted English full name
        dob_ec         : date of birth in Ethiopian Calendar
        dob_gc         : date of birth in Gregorian Calendar
        id_number      : national ID number
        gender         : Male / Female
        expiry_gc      : expiry date in Gregorian Calendar
        expiry_ec      : expiry date in Ethiopian Calendar
    """
    processed = preprocess_image(img)
    raw_text = pytesseract.image_to_string(processed, config=r'--oem 3 --psm 6')

    lines = [line.strip() for line in raw_text.splitlines() if line.strip()]
    all_text = " ".join(lines)

    extracted = {
        "raw_text":  raw_text,
        "lines":     lines,
        "full_name": None,
        "dob_ec":    None,
        "dob_gc":    None,
        "id_number": None,
        "gender":    None,
        "expiry_gc": None,
        "expiry_ec": None,
    }

    # --- Full Name ---
    # Card layout: label line → Amharic line → English line
    # Strategy: read up to 3 lines after label, pick best fuzzy match
    name_label_idx = find_label_line_index(lines, ["full name", "name"])
    if name_label_idx is not None:
        candidates = []
        for offset in range(1, 4):
            idx = name_label_idx + offset
            if idx >= len(lines):
                break
            cleaned = clean_ocr_noise(lines[idx])
            if cleaned:
                candidates.append(cleaned)
        # We return all candidates — caller picks best match against user input
        extracted["_name_candidates"] = candidates
        # Default to last candidate (usually the English one)
        if candidates:
            extracted["full_name"] = candidates[-1]

    # --- DOB: label → next line → EC | GC ---
    dob_label_idx = find_label_line_index(lines, ["date of birth", "dob", "birth"])
    if dob_label_idx is not None and dob_label_idx + 1 < len(lines):
        dates = extract_dates_from(lines[dob_label_idx + 1])
        if len(dates) >= 1:
            extracted["dob_ec"] = dates[0]
        if len(dates) >= 2:
            extracted["dob_gc"] = dates[1]

    # --- Date of Expiry: label → next line → GC | EC ---
    expiry_label_idx = find_label_line_index(lines, ["date of expiry", "expiry", "expir"])
    if expiry_label_idx is not None and expiry_label_idx + 1 < len(lines):
        dates = extract_dates_from(lines[expiry_label_idx + 1])
        if len(dates) >= 1:
            extracted["expiry_gc"] = dates[0]
        if len(dates) >= 2:
            extracted["expiry_ec"] = dates[1]

    # --- ID Number: long digit sequence (12–20 digits) ---
    id_match = re.search(r'\b(\d[\d\s]{10,20}\d)\b', all_text)
    if id_match:
        extracted["id_number"] = id_match.group(1).replace(" ", "")

    # --- Gender ---
    gender_match = re.search(r'\b(Male|Female)\b', all_text, re.IGNORECASE)
    if gender_match:
        extracted["gender"] = gender_match.group(1).capitalize()
    else:
        sex_match = re.search(r'\bsex\b[^\w]*([MF])\b', all_text, re.IGNORECASE)
        if sex_match:
            extracted["gender"] = "Male" if sex_match.group(1).upper() == "M" else "Female"

    return extracted


# -----------------------------
# Verification: compare extracted fields against user input
# -----------------------------
def verify_ocr(img: np.ndarray, user_input: dict) -> dict:
    """
    Extract fields from ID image and compare against user-submitted form data.

    user_input keys expected:
        full_name, dob, id_number, gender, expiry_date

    Returns:
        extracted_fields : what OCR found
        match_scores     : per-field fuzzy match % vs user input
        ocr_confidence   : overall confidence score (0.0–1.0) for ai_verification table
        overall_status   : "verified" | "manual_review" | "rejected"
        passed           : bool
    """
    extracted = extract_id_fields(img)

    # --- Pick best full name candidate vs user input ---
    candidates = extracted.pop("_name_candidates", [])
    if candidates:
        best_name = max(
            candidates,
            key=lambda c: fuzz.token_sort_ratio(user_input.get("full_name", ""), c)
        )
        extracted["full_name"] = best_name

    # --- Fuzzy match scores ---
    match_scores = {}

    # Full name: token_sort_ratio handles word order differences
    name_extracted = extracted.get("full_name") or ""
    name_registered = user_input.get("full_name", "")
    match_scores["full_name"] = (
        fuzz.token_sort_ratio(name_registered, name_extracted)
        if name_extracted else 0.0
    )

    # DOB: match EC date against what user registered
    dob_extracted = extracted.get("dob_ec") or ""
    dob_registered = user_input.get("dob", "")
    match_scores["dob"] = (
        fuzz.ratio(dob_registered, dob_extracted)
        if dob_extracted else 0.0
    )

    # ID number: exact ratio
    id_extracted = extracted.get("id_number") or ""
    id_registered = user_input.get("id_number", "")
    match_scores["id_number"] = (
        fuzz.ratio(id_registered, id_extracted)
        if id_extracted else 0.0
    )

    # Gender
    gender_extracted = extracted.get("gender") or ""
    gender_registered = user_input.get("gender", "")
    match_scores["gender"] = (
        fuzz.ratio(gender_registered, gender_extracted)
        if gender_extracted else 0.0
    )

    # Expiry date (validity check — not part of identity score)
    expiry_extracted = extracted.get("expiry_gc") or ""
    expiry_registered = user_input.get("expiry_date", "")
    match_scores["expiry_date"] = (
        fuzz.ratio(expiry_registered, expiry_extracted)
        if expiry_extracted else 0.0
    )

    # --- Overall OCR confidence score ---
    ocr_confidence = compute_ocr_confidence(match_scores)

    # --- Determine status ---
    core_avg = (
        match_scores["full_name"] +
        match_scores["dob"] +
        match_scores["id_number"] +
        match_scores["gender"]
    ) / 4.0

    if ocr_confidence >= OCR_MIN_SCORE and core_avg >= NAME_MATCH_MIN_SCORE:
        overall_status = "verified"
        passed = True
    elif ocr_confidence >= (OCR_MIN_SCORE * 0.75):
        overall_status = "manual_review"
        passed = False
    else:
        overall_status = "rejected"
        passed = False

    return {
        "extracted_fields": {
            "full_name":  extracted.get("full_name"),
            "dob_ec":     extracted.get("dob_ec"),
            "dob_gc":     extracted.get("dob_gc"),
            "id_number":  extracted.get("id_number"),
            "gender":     extracted.get("gender"),
            "expiry_gc":  extracted.get("expiry_gc"),
            "expiry_ec":  extracted.get("expiry_ec"),
        },
        "match_scores":    match_scores,
        "ocr_confidence":  ocr_confidence,
        "overall_status":  overall_status,
        "passed":          passed,
    }