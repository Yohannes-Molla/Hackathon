import os

from dotenv import load_dotenv

load_dotenv()

# -----------------------------
# App
# -----------------------------
APP_NAME    = "Kifiya eKYC AI Service"
APP_VERSION = "1.0.0"
DEBUG       = os.getenv("DEBUG", "false").lower() == "true"

# -----------------------------
# Tesseract OCR
# -----------------------------
TESSERACT_CMD = os.getenv(
    "TESSERACT_CMD",
    "C:/Program Files/Tesseract-OCR/tesseract.exe"
)

os.environ["DEEPFACE_HOME"] = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "models", "weights")
)

# -----------------------------
# DeepFace
# -----------------------------
DEEPFACE_MODEL          = "Facenet512"    # 512-dim — matches pgvector vector(512) on BE side
DEEPFACE_DETECTOR       = "opencv"        # opencv | retinaface | mtcnn
DEEPFACE_METRIC         = "cosine"
EMBEDDING_MODEL_VERSION = "deepface_facenet512_v1"   # BE stores this in biometric_templates

# -----------------------------
# AI Score Thresholds
# All sourced from schema ai_verification table comments
# -----------------------------

# OCR — below this triggers manual_review on BE side
OCR_MIN_SCORE = float(os.getenv("OCR_MIN_SCORE", "0.75"))

# Face similarity — must be >= this to pass
FACE_MIN_SCORE = float(os.getenv("FACE_MIN_SCORE", "0.85"))

# Liveness confidence — must be >= this to pass
LIVENESS_MIN_SCORE = float(os.getenv("LIVENESS_MIN_SCORE", "0.90"))

# Deepfake — above this = reject immediately
DEEPFAKE_MAX_SCORE = float(os.getenv("DEEPFAKE_MAX_SCORE", "0.30"))

# Fraud — above this = manual_review_queue
FRAUD_REVIEW_THRESHOLD = float(os.getenv("FRAUD_REVIEW_THRESHOLD", "0.50"))

# Fraud — above this = hard reject
FRAUD_REJECT_THRESHOLD = float(os.getenv("FRAUD_REJECT_THRESHOLD", "0.80"))

# Fuzzy name match — below this = flag as mismatch
NAME_MATCH_MIN_SCORE = float(os.getenv("NAME_MATCH_MIN_SCORE", "80.0"))

# -----------------------------
# Liveness Detection
# -----------------------------
LIVENESS_MIN_FRAMES      = int(os.getenv("LIVENESS_MIN_FRAMES", "5"))
LIVENESS_MAX_FRAMES      = int(os.getenv("LIVENESS_MAX_FRAMES", "15"))
EYE_BLINK_THRESHOLD      = float(os.getenv("EYE_BLINK_THRESHOLD", "0.25"))
HEAD_MOVEMENT_MIN_PIXELS = int(os.getenv("HEAD_MOVEMENT_MIN_PIXELS", "30"))

# -----------------------------
# Fraud Score Weights
# Must sum to 1.0
# -----------------------------
FRAUD_WEIGHT_OCR      = float(os.getenv("FRAUD_WEIGHT_OCR",      "0.25"))
FRAUD_WEIGHT_FACE     = float(os.getenv("FRAUD_WEIGHT_FACE",      "0.35"))
FRAUD_WEIGHT_LIVENESS = float(os.getenv("FRAUD_WEIGHT_LIVENESS",  "0.25"))
FRAUD_WEIGHT_DEEPFAKE = float(os.getenv("FRAUD_WEIGHT_DEEPFAKE",  "0.15"))

# -----------------------------
# Startup validation
# Called once when FastAPI starts — catches bad config early
# -----------------------------
def validate_config():
    errors = []

    if not os.path.exists(TESSERACT_CMD):
        errors.append(f"TESSERACT_CMD not found: {TESSERACT_CMD}")

    weights_sum = round(
        FRAUD_WEIGHT_OCR + FRAUD_WEIGHT_FACE +
        FRAUD_WEIGHT_LIVENESS + FRAUD_WEIGHT_DEEPFAKE,
        4
    )
    if weights_sum != 1.0:
        errors.append(
            f"Fraud weights must sum to 1.0 — current sum: {weights_sum}"
        )

    if errors:
        raise ValueError(
            "Config validation failed:\n" +
            "\n".join(f"  - {e}" for e in errors)
        )

    print(f" {APP_NAME} v{APP_VERSION} — config loaded")
    print(f"   Tesseract  : {TESSERACT_CMD}")
    print(f"   DeepFace   : {DEEPFACE_MODEL} / {DEEPFACE_DETECTOR} / {DEEPFACE_METRIC}")
    print(f"   Thresholds : OCR>={OCR_MIN_SCORE} | Face>={FACE_MIN_SCORE} | "
          f"Liveness>={LIVENESS_MIN_SCORE} | Deepfake<={DEEPFAKE_MAX_SCORE}")
    print(f"   Fraud weights : OCR={FRAUD_WEIGHT_OCR} | Face={FRAUD_WEIGHT_FACE} | "
          f"Liveness={FRAUD_WEIGHT_LIVENESS} | Deepfake={FRAUD_WEIGHT_DEEPFAKE}")