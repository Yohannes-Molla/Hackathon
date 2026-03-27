import os
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"   # suppress TensorFlow noise before import

import cv2
import numpy as np
from deepface import DeepFace

from app.core.config import (
    DEEPFACE_MODEL,
    DEEPFACE_DETECTOR,
    DEEPFACE_METRIC,
    EMBEDDING_MODEL_VERSION,
    FACE_MIN_SCORE,
)


# -----------------------------
# Helper: decode raw bytes → OpenCV image
# -----------------------------
def bytes_to_image(file_bytes: bytes) -> np.ndarray:
    """Convert raw file bytes (from UploadFile.read()) to an OpenCV BGR image."""
    arr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Could not decode image — file may be corrupt or unsupported format")
    return img


# -----------------------------
# Helper: save numpy image to a temp file
# DeepFace.verify() and DeepFace.represent() accept file paths.
# We write to a temp file, use it, then delete it immediately.
# -----------------------------
def _write_temp(img: np.ndarray, name: str) -> str:
    path = f"_temp_{name}.jpg"
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
# Extract face embedding from an image
# Returns a 512-dim numpy array (Facenet512)
# BE stores this in biometric_templates.face_embedding (pgvector)
# -----------------------------
def extract_embedding(img: np.ndarray) -> np.ndarray:
    """
    Extract a 512-dimensional face embedding from an image.

    Args:
        img: OpenCV BGR image (numpy array)

    Returns:
        numpy array of shape (512,) — L2 normalized face embedding

    Raises:
        ValueError: if no face is detected in the image
    """
    tmp = _write_temp(img, "embed")
    try:
        result = DeepFace.represent(
            img_path         = tmp,
            model_name       = DEEPFACE_MODEL,
            detector_backend = DEEPFACE_DETECTOR,
            enforce_detection = True,
        )
        # DeepFace.represent returns a list — take the first (largest) face
        embedding = np.array(result[0]["embedding"], dtype=np.float32)

        # L2 normalize so dot product = cosine similarity
        norm = np.linalg.norm(embedding)
        if norm > 0:
            embedding = embedding / norm

        return embedding

    except ValueError as e:
        raise ValueError(f"Face not detected in image: {e}")
    finally:
        _cleanup(tmp)


# -----------------------------
# Compare two images — returns similarity score
# Used in /verify-face endpoint
# -----------------------------
def verify_face(
    id_img: np.ndarray,
    selfie_img: np.ndarray
) -> dict:
    """
    Compare the face on an ID card against a selfie photo.

    Args:
        id_img     : OpenCV image of the ID card (face will be detected from it)
        selfie_img : OpenCV image of the selfie

    Returns:
        {
            similarity_pct      : float  — 0.0 to 100.0
            verified            : bool   — True if similarity >= FACE_MIN_SCORE
            distance            : float  — raw cosine distance (lower = more similar)
            threshold           : float  — DeepFace internal threshold
            selfie_embedding    : list   — 512 floats, BE stores in pgvector
            embedding_model     : str    — model version label for BE to store
            passed              : bool
        }
    """
    tmp_id     = _write_temp(id_img,     "id")
    tmp_selfie = _write_temp(selfie_img, "selfie")

    try:
        # --- Face comparison ---
        result = DeepFace.verify(
            img1_path        = tmp_id,
            img2_path        = tmp_selfie,
            model_name       = DEEPFACE_MODEL,
            detector_backend = DEEPFACE_DETECTOR,
            distance_metric  = DEEPFACE_METRIC,
            enforce_detection = True,
        )

        distance       = result["distance"]
        threshold      = result["threshold"]
        verified       = result["verified"]
        similarity_pct = round((1 - distance) * 100, 4)

        # --- Extract selfie embedding for BE to store ---
        selfie_embedding = extract_embedding(selfie_img)

        # Override DeepFace's internal threshold check with our config threshold
        passed = similarity_pct >= (FACE_MIN_SCORE * 100)

        return {
            "similarity_pct":   similarity_pct,
            "verified":         verified,
            "distance":         round(distance, 6),
            "threshold":        threshold,
            "selfie_embedding": selfie_embedding.tolist(),   # list of 512 floats
            "embedding_model":  EMBEDDING_MODEL_VERSION,
            "passed":           passed,
        }

    except ValueError as e:
        raise ValueError(f"Face verification failed: {e}")

    finally:
        _cleanup(tmp_id, tmp_selfie)