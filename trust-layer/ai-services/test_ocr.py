import os
import re
import cv2
import pytesseract
from rapidfuzz import fuzz, process
from dotenv import load_dotenv

# -----------------------------
# Load environment variables
# -----------------------------
load_dotenv()
tesseract_cmd = os.getenv("TESSERACT_CMD")
if not tesseract_cmd or not os.path.exists(tesseract_cmd):
    raise ValueError("TESSERACT_CMD not found or incorrect in .env")
pytesseract.pytesseract.tesseract_cmd = tesseract_cmd
print("✅ Using Tesseract at:", pytesseract.pytesseract.tesseract_cmd)

# -----------------------------
# User input (simulated registration data)
# -----------------------------
user_input = {
    "full_name":   "Abdurezak Abazinab Abalencho",
    "dob":         "13/06/1996",       # EC date
    "id_number":   "3421395862364820",
    "gender":      "Male",
    "expiry_date": "2026/06/20"        # GC expiry
}

# -----------------------------
# Load ID image
# -----------------------------
image_path = "sample_id.jpg"
img = cv2.imread(image_path)
if img is None:
    raise FileNotFoundError(f"Image not found: {image_path}")

# -----------------------------
# Preprocessing
# -----------------------------
def preprocess_image(image):
    scale = 2
    image = cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.fastNlMeansDenoising(gray, h=30)
    thresh = cv2.adaptiveThreshold(
        gray, 255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        blockSize=31,
        C=10
    )
    return thresh

processed = preprocess_image(img)
cv2.imwrite("debug_preprocessed.jpg", processed)
print("📸 Saved preprocessed image: debug_preprocessed.jpg")

# -----------------------------
# Extract ALL raw text
# -----------------------------
custom_config = r'--oem 3 --psm 6'
raw_text = pytesseract.image_to_string(processed, config=custom_config)

print("\n=== RAW OCR OUTPUT ===")
print(raw_text)
print("=" * 40)

lines = [line.strip() for line in raw_text.splitlines() if line.strip()]
all_text = " ".join(lines)

print("\n=== CLEANED LINES ===")
for i, line in enumerate(lines):
    print(f"  [{i}] {line}")

# -----------------------------
# Helpers
# -----------------------------
DATE_PATTERN = re.compile(
    r'\b(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{4}'
    r'|\d{4}[\/\-]\d{1,2}[\/\-]\d{1,2}'
    r'|\d{4}[\/\-][A-Za-z]{3}[\/\-]\d{1,2})\b'
)

def extract_dates_from(text):
    return DATE_PATTERN.findall(text)

def find_label_line_index(lines, keywords):
    """Return index of the first line containing any of the keywords."""
    for i, line in enumerate(lines):
        if any(kw.lower() in line.lower() for kw in keywords):
            return i
    return None

def clean_ocr_noise(text):
    """
    Strip non-alphanumeric noise and short junk tokens from edges.
    e.g. "an _ Abdurezak Abazinab Abalencho sy" → "Abdurezak Abazinab Abalencho"
    """
    text = re.sub(r'[^A-Za-z0-9\s\/\-]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    tokens = text.split()

    def is_noise(tok):
        return len(tok) <= 2 or not tok.isalpha()

    while tokens and is_noise(tokens[0]):
        tokens.pop(0)
    while tokens and is_noise(tokens[-1]):
        tokens.pop()

    return " ".join(tokens)

# -----------------------------
# Field Extraction
# -----------------------------
extracted_fields = {
    "full_name":  None,
    "dob_ec":     None,
    "dob_gc":     None,
    "id_number":  None,
    "gender":     None,
    "expiry_gc":  None,
    "expiry_ec":  None,
}

# -------------------------------------------------------
# Full Name
#
# Card layout:
#   Line [2]: "... | Full Name |"          ← label
#   Line [3]: "Bo. ~ — ANELUP ANT ANAT"   ← Amharic transliterated (Latin chars!)
#   Line [4]: "an _ Abdurezak Abazinabd…" ← English name ← we want this
#
# The Latin-check approach fails because the Amharic transliteration
# also uses Latin characters.
#
# Fix: read the next 3 lines after the label, clean each one,
# then pick the one with the HIGHEST fuzzy match score against
# the registered name — since the user typed their English name
# at registration, the English line will always score higher.
# -------------------------------------------------------
name_label_idx = find_label_line_index(lines, ["full name", "name"])
if name_label_idx is not None:
    print(f"\n👤 'Full Name' label found at line [{name_label_idx}]")

    best_name = None
    best_score = -1

    for offset in range(1, 4):   # check lines +1, +2, +3
        candidate_idx = name_label_idx + offset
        if candidate_idx >= len(lines):
            break
        candidate = lines[candidate_idx]
        cleaned = clean_ocr_noise(candidate)
        if not cleaned:
            continue
        score = fuzz.token_sort_ratio(user_input["full_name"], cleaned)
        print(f"   Line [{candidate_idx}]: {candidate!r}")
        print(f"             cleaned → {cleaned!r}  |  fuzzy score: {score:.1f}")
        if score > best_score:
            best_score = score
            best_name = cleaned

    extracted_fields["full_name"] = best_name
    print(f"   ✅ Best match: {best_name!r} (score: {best_score:.1f})")

# Fuzzy fallback across all lines if label-based search failed
if not extracted_fields["full_name"]:
    print("⚠️ Falling back to fuzzy name search across all lines")
    best_match = process.extractOne(
        user_input["full_name"], lines, scorer=fuzz.token_sort_ratio
    )
    if best_match and best_match[1] >= 40:
        extracted_fields["full_name"] = clean_ocr_noise(best_match[0])
        print(f"👤 Full Name via fuzzy fallback: {extracted_fields['full_name']!r} (score: {best_match[1]:.1f})")

# -------------------------------------------------------
# DOB — label line → next line → split EC | GC
# -------------------------------------------------------
dob_label_idx = find_label_line_index(lines, ["date of birth", "dob", "birth"])
if dob_label_idx is not None and dob_label_idx + 1 < len(lines):
    dob_value_line = lines[dob_label_idx + 1]
    print(f"\n📅 DOB label at line [{dob_label_idx}], value line [{dob_label_idx+1}]: {dob_value_line!r}")
    dates = extract_dates_from(dob_value_line)
    if len(dates) >= 1:
        extracted_fields["dob_ec"] = dates[0]
        print(f"   EC DOB: {dates[0]}")
    if len(dates) >= 2:
        extracted_fields["dob_gc"] = dates[1]
        print(f"   GC DOB: {dates[1]}")
else:
    print("\n⚠️ DOB label not found, falling back to first date in text")
    all_dates = extract_dates_from(all_text)
    if all_dates:
        extracted_fields["dob_ec"] = all_dates[0]

# -------------------------------------------------------
# Date of Expiry — label line → next line → split GC | EC
# -------------------------------------------------------
expiry_label_idx = find_label_line_index(lines, ["date of expiry", "expiry", "expir"])
if expiry_label_idx is not None and expiry_label_idx + 1 < len(lines):
    expiry_value_line = lines[expiry_label_idx + 1]
    print(f"\n📅 Expiry label at line [{expiry_label_idx}], value line [{expiry_label_idx+1}]: {expiry_value_line!r}")
    dates = extract_dates_from(expiry_value_line)
    if len(dates) >= 1:
        extracted_fields["expiry_gc"] = dates[0]
        print(f"   GC Expiry: {dates[0]}")
    if len(dates) >= 2:
        extracted_fields["expiry_ec"] = dates[1]
        print(f"   EC Expiry: {dates[1]}")
else:
    print("\n⚠️ Expiry label not found in OCR lines")

# -------------------------------------------------------
# ID Number — long digit sequence (12–20 digits)
# -------------------------------------------------------
id_pattern = re.compile(r'\b(\d[\d\s]{10,20}\d)\b')
id_matches = id_pattern.findall(all_text)
if id_matches:
    best_id = id_matches[0].replace(" ", "")
    extracted_fields["id_number"] = best_id
    print(f"\n🪪 ID Number found via regex: {best_id}")

# -------------------------------------------------------
# Gender
# -------------------------------------------------------
gender_pattern = re.compile(r'\b(Male|Female)\b', re.IGNORECASE)
gender_match = gender_pattern.search(all_text)
if gender_match:
    extracted_fields["gender"] = gender_match.group(1).capitalize()
    print(f"⚧ Gender found via regex: {extracted_fields['gender']}")
else:
    sex_label_match = re.search(r'\bsex\b[^\w]*([MF])\b', all_text, re.IGNORECASE)
    if sex_label_match:
        letter = sex_label_match.group(1).upper()
        extracted_fields["gender"] = "Male" if letter == "M" else "Female"
        print(f"⚧ Gender found via sex label: {extracted_fields['gender']}")

# -----------------------------
# Results
# -----------------------------
print("\n=== EXTRACTED FIELDS ===")
print(f"  full_name  : {extracted_fields['full_name']!r}")
print(f"  dob (EC)   : {extracted_fields['dob_ec']!r}")
print(f"  dob (GC)   : {extracted_fields['dob_gc']!r}")
print(f"  id_number  : {extracted_fields['id_number']!r}")
print(f"  gender     : {extracted_fields['gender']!r}")
print(f"  expiry (GC): {extracted_fields['expiry_gc']!r}")
print(f"  expiry (EC): {extracted_fields['expiry_ec']!r}")

# -----------------------------
# Match scores vs user input
# -----------------------------
fields_for_matching = {
    "full_name":   extracted_fields["full_name"],
    "dob":         extracted_fields["dob_ec"],
    "id_number":   extracted_fields["id_number"],
    "gender":      extracted_fields["gender"],
    "expiry_date": extracted_fields["expiry_gc"],
}

match_scores = {}
for k in ["full_name", "dob", "id_number", "gender", "expiry_date"]:
    extracted = fields_for_matching.get(k) or ""
    registered = user_input.get(k, "")
    if extracted:
        score = fuzz.token_sort_ratio(registered, extracted) if k == "full_name" else fuzz.ratio(registered, extracted)
    else:
        score = 0.0
    match_scores[k] = score

print("\n=== MATCH SCORES ===")
for k, v in match_scores.items():
    status = "✅" if v >= 80 else "⚠️" if v >= 50 else "❌"
    print(f"  {status} {k}: {v:.1f}%")

core_fields = ["full_name", "dob", "id_number", "gender"]
average_score = sum(match_scores[k] for k in core_fields) / len(core_fields)
print(f"\n=== AVERAGE VERIFICATION SCORE (core fields): {average_score:.2f}% ===")
print(f"📋 Expiry Date match: {match_scores['expiry_date']:.1f}% (validity check — not in average)")

if average_score >= 90:
    print("✅ Verification PASSED")
elif average_score >= 70:
    print("⚠️ Low Confidence — Manual Review Recommended")
else:
    print("❌ Verification FAILED")

# -----------------------------
# Save debug output
# -----------------------------
with open("debug_ocr_output.txt", "w", encoding="utf-8") as f:
    f.write("=== RAW OCR TEXT ===\n")
    f.write(raw_text)
    f.write("\n\n=== EXTRACTED FIELDS ===\n")
    for k, v in extracted_fields.items():
        f.write(f"{k}: {v}\n")
    f.write("\n=== MATCH SCORES ===\n")
    for k, v in match_scores.items():
        f.write(f"{k}: {v:.1f}%\n")
    f.write(f"\nAVERAGE (core fields): {average_score:.2f}%\n")

print("\n📄 Full debug output saved to: debug_ocr_output.txt")