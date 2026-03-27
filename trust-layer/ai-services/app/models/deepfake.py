import os
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

import cv2
import numpy as np
from deepface import DeepFace

from app.core.config import DEEPFAKE_MAX_SCORE, DEEPFACE_DETECTOR


# -----------------------------
# Helper: decode raw bytes → OpenCV image
# -----------------------------
def bytes_to_image(file_bytes: bytes) -> np.ndarray:
    arr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Could not decode image — file may be corrupt")
    return img


def _write_temp(img: np.ndarray, name: str) -> str:
    path = f"_temp_deepfake_{name}.jpg"
    cv2.imwrite(path, img)
    return path


def _cleanup(*paths: str):
    for p in paths:
        try:
            if os.path.exists(p):
                os.remove(p)
        except Exception:
            pass


# -----------------------------
# Deepfake detection
#
# DeepFace does not have a dedicated deepfake model,
# but we can use face quality signals as a proxy:
#
# Real photos tend to have:
#   - natural facial landmarks
#   - consistent lighting across the face
#   - normal face confidence score from the detector
#
# AI-generated / deepfake images tend to have:
#   - unusually high or perfect detector confidence
#   - inconsistent texture around eyes/mouth edges
#   - face region symmetry anomalies
#
# For the hackathon we use:
#   1. DeepFace detector confidence score
#   2. Image texture analysis (Laplacian variance — blur detection)
#   3. Face symmetry score
# Combined into a single deepfake_score (0.0–1.0)
# where higher = more likely fake
# -----------------------------

def _laplacian_variance(img: np.ndarray) -> float:
    """
    Measure image sharpness using Laplacian variance.
    Real photos have natural sharpness variation.
    AI images tend to be unnaturally sharp or unnaturally blurry.
    Returns a normalized score 0.0–1.0 (higher = more suspicious)
    """
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    variance = cv2.Laplacian(gray, cv2.CV_64F).var()

    # Very low variance = blurry (possibly manipulated)
    # Very high variance = unnaturally sharp (possibly AI-generated)
    # Normal range for real photos: 100–2000
    if variance < 50:
        return 0.8    # very blurry — suspicious
    elif variance > 3000:
        return 0.6    # unnaturally sharp — suspicious
    else:
        return 0.1    # normal range — likely real


def _face_symmetry_score(img: np.ndarray) -> float:
    """
    Check face symmetry by comparing left and right halves.
    AI-generated faces are often TOO symmetric — uncanny valley.
    Real faces have slight natural asymmetry.
    Returns suspicion score 0.0–1.0 (higher = more suspicious)
    """
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape

    # Crop to center region and compare left vs right half
    left  = gray[:, :w // 2]
    right = cv2.flip(gray[:, w // 2:], 1)

    # Resize to same shape if widths differ by 1 pixel
    min_w = min(left.shape[1], right.shape[1])
    left  = left[:, :min_w]
    right = right[:, :min_w]

    # Normalized difference — lower diff = more symmetric
    diff = np.mean(np.abs(left.astype(float) - right.astype(float))) / 255.0

    # Real faces: diff typically 0.05–0.20
    # AI faces: diff < 0.03 (too symmetric) or > 0.30 (inconsistent generation)
    if diff < 0.03:
        return 0.7   # too symmetric — suspicious
    elif diff > 0.30:
        return 0.5   # too asymmetric — suspicious
    else:
        return 0.1   # natural asymmetry — likely real


def _detector_confidence(img: np.ndarray) -> tuple[float, float]:
    """
    Get face detector confidence from DeepFace.
    Returns (confidence, suspicion_score)
    Perfect confidence (1.0) on a selfie is slightly suspicious.
    """
    tmp = _write_temp(img, "conf")
    try:
        faces = DeepFace.extract_faces(
            img_path         = tmp,
            detector_backend = DEEPFACE_DETECTOR,
            enforce_detection = False,
        )
        if not faces:
            return 0.0, 0.5   # no face found — suspicious

        confidence = faces[0].get("confidence", 0.5)

        # Suspiciously perfect confidence
        if confidence >= 0.999:
            suspicion = 0.5
        elif confidence >= 0.95:
            suspicion = 0.1   # high but normal
        elif confidence < 0.50:
            suspicion = 0.6   # low confidence — possibly fake or bad photo
        else:
            suspicion = 0.2

        return confidence, suspicion
    except Exception:
        return 0.0, 0.4
    finally:
        _cleanup(tmp)


# -----------------------------
# Main deepfake detection function
# -----------------------------
def detect_deepfake(img: np.ndarray) -> dict:
    """
    Analyze a selfie image for deepfake / AI-generation signals.

    Args:
        img: OpenCV BGR image (selfie)

    Returns:
        {
            deepfake_score   : float  (0.0–1.0, higher = more likely fake)
            is_deepfake      : bool   (True if score > DEEPFAKE_MAX_SCORE)
            passed           : bool   (True if not deepfake)
            signals          : dict   (individual signal scores for audit)
        }
    """
    # Run all signal checks
    texture_score   = _laplacian_variance(img)
    symmetry_score  = _face_symmetry_score(img)
    confidence, detector_suspicion = _detector_confidence(img)

    # Weighted combination
    # Texture and symmetry are most reliable for hackathon purposes
    deepfake_score = round(
        texture_score       * 0.35 +
        symmetry_score      * 0.35 +
        detector_suspicion  * 0.30,
        4
    )

    is_deepfake = deepfake_score > DEEPFAKE_MAX_SCORE
    passed      = not is_deepfake

    return {
        "deepfake_score": deepfake_score,
        "is_deepfake":    is_deepfake,
        "passed":         passed,
        "signals": {
            "texture_suspicion":   round(texture_score, 4),
            "symmetry_suspicion":  round(symmetry_score, 4),
            "detector_confidence": round(confidence, 4),
            "detector_suspicion":  round(detector_suspicion, 4),
        },
    }