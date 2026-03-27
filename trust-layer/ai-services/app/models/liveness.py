import cv2
import numpy as np
from app.core.config import (
    LIVENESS_MIN_FRAMES,
    EYE_BLINK_THRESHOLD,
    HEAD_MOVEMENT_MIN_PIXELS,
    LIVENESS_MIN_SCORE,
)

# -----------------------------
# Load facial landmark detector
# Uses OpenCV's built-in face + eye cascades — no extra install needed
# -----------------------------
_face_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
)
_eye_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + "haarcascade_eye.xml"
)


# -----------------------------
# Helper: decode raw bytes → OpenCV image
# -----------------------------
def bytes_to_image(file_bytes: bytes) -> np.ndarray:
    arr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Could not decode frame — file may be corrupt")
    return img


# -----------------------------
# Helper: detect face bounding box center in a frame
# Returns (cx, cy) or None if no face found
# -----------------------------
def _get_face_center(frame: np.ndarray) -> tuple[int, int] | None:
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    faces = _face_cascade.detectMultiScale(
        gray, scaleFactor=1.1, minNeighbors=5, minSize=(60, 60)
    )
    if len(faces) == 0:
        return None
    # Use largest face
    x, y, w, h = sorted(faces, key=lambda f: f[2] * f[3], reverse=True)[0]
    return (x + w // 2, y + h // 2)


# -----------------------------
# Helper: count eyes visible in a frame
# Returns number of eyes detected (0, 1, or 2)
# -----------------------------
def _count_eyes(frame: np.ndarray) -> int:
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    faces = _face_cascade.detectMultiScale(
        gray, scaleFactor=1.1, minNeighbors=5, minSize=(60, 60)
    )
    if len(faces) == 0:
        return 0

    # Check largest face only
    x, y, w, h = sorted(faces, key=lambda f: f[2] * f[3], reverse=True)[0]
    face_roi = gray[y:y + h, x:x + w]

    eyes = _eye_cascade.detectMultiScale(
        face_roi, scaleFactor=1.1, minNeighbors=5, minSize=(20, 20)
    )
    return len(eyes)


# -----------------------------
# Blink detection
# Strategy: across N frames, look for a transition:
#   eyes open (2 eyes) → eyes closed (0 eyes) → eyes open (2 eyes)
# That sequence = one blink detected
# -----------------------------
def detect_blink(frames: list[np.ndarray]) -> dict:
    """
    Detect eye blink across a sequence of frames.

    Returns:
        blink_detected : bool
        blink_count    : int
        eye_counts     : list of eye counts per frame (for debug)
    """
    eye_counts = [_count_eyes(f) for f in frames]

    blink_count = 0
    i = 0
    while i < len(eye_counts) - 2:
        # Pattern: open (>=1) → closed (0) → open (>=1)
        if eye_counts[i] >= 1 and eye_counts[i + 1] == 0 and eye_counts[i + 2] >= 1:
            blink_count += 1
            i += 3   # skip past this blink window
        else:
            i += 1

    return {
        "blink_detected": blink_count >= 1,
        "blink_count":    blink_count,
        "eye_counts":     eye_counts,
    }


# -----------------------------
# Head movement detection
# Strategy: track face center across frames.
# If total horizontal or vertical shift > threshold → movement detected
# -----------------------------
def detect_head_movement(frames: list[np.ndarray]) -> dict:
    """
    Detect head movement across a sequence of frames.

    Returns:
        head_movement_detected : bool
        max_shift_pixels       : int — max pixel shift observed
        face_centers           : list of (cx, cy) per frame
    """
    centers = []
    for f in frames:
        center = _get_face_center(f)
        if center is not None:
            centers.append(center)

    if len(centers) < 2:
        return {
            "head_movement_detected": False,
            "max_shift_pixels":       0,
            "face_centers":           centers,
        }

    # Compute max displacement from first detected center
    origin_x, origin_y = centers[0]
    max_shift = 0
    for cx, cy in centers[1:]:
        shift = max(abs(cx - origin_x), abs(cy - origin_y))
        if shift > max_shift:
            max_shift = shift

    return {
        "head_movement_detected": max_shift >= HEAD_MOVEMENT_MIN_PIXELS,
        "max_shift_pixels":       max_shift,
        "face_centers":           centers,
    }


# -----------------------------
# Compute liveness score (0.0–1.0)
# -----------------------------
def _compute_liveness_score(
    blink_detected: bool,
    head_movement_detected: bool,
    frames_with_face: int,
    total_frames: int,
) -> float:
    """
    Combine signals into a single liveness confidence score.

    Weights:
        blink detection        : 40%
        head movement          : 40%
        face presence ratio    : 20%  (how many frames had a detectable face)
    """
    blink_score    = 1.0 if blink_detected else 0.0
    movement_score = 1.0 if head_movement_detected else 0.0
    presence_score = frames_with_face / total_frames if total_frames > 0 else 0.0

    score = (
        blink_score    * 0.40 +
        movement_score * 0.40 +
        presence_score * 0.20
    )
    return round(score, 4)


# -----------------------------
# Main liveness verification function
# Called by /verify-liveness endpoint
# -----------------------------
def verify_liveness(frames_bytes: list[bytes]) -> dict:
    """
    Run liveness check across a list of raw frame bytes.

    Args:
        frames_bytes: list of raw image bytes (from UploadFile.read())
                      BE should send 5–15 frames captured from webcam

    Returns:
        {
            blink_detected          : bool
            blink_count             : int
            head_movement_detected  : bool
            max_shift_pixels        : int
            liveness_score          : float  (0.0–1.0)
            liveness_passed         : bool
            frames_received         : int
            frames_with_face        : int
            details                 : dict   (eye_counts, face_centers)
        }
    """
    if len(frames_bytes) < LIVENESS_MIN_FRAMES:
        raise ValueError(
            f"Too few frames: received {len(frames_bytes)}, "
            f"minimum required is {LIVENESS_MIN_FRAMES}"
        )

    # Decode all frames
    frames = []
    for i, fb in enumerate(frames_bytes):
        try:
            frames.append(bytes_to_image(fb))
        except ValueError:
            # Skip corrupt frames but don't fail the whole check
            continue

    if len(frames) < LIVENESS_MIN_FRAMES:
        raise ValueError(
            f"Too many corrupt frames — only {len(frames)} decoded successfully"
        )

    # Count frames where a face was detected
    frames_with_face = sum(
        1 for f in frames if _get_face_center(f) is not None
    )

    # Run checks
    blink_result    = detect_blink(frames)
    movement_result = detect_head_movement(frames)

    liveness_score = _compute_liveness_score(
        blink_detected         = blink_result["blink_detected"],
        head_movement_detected = movement_result["head_movement_detected"],
        frames_with_face       = frames_with_face,
        total_frames           = len(frames),
    )

    liveness_passed = liveness_score >= LIVENESS_MIN_SCORE

    return {
        "blink_detected":          blink_result["blink_detected"],
        "blink_count":             blink_result["blink_count"],
        "head_movement_detected":  movement_result["head_movement_detected"],
        "max_shift_pixels":        movement_result["max_shift_pixels"],
        "liveness_score":          liveness_score,
        "liveness_passed":         liveness_passed,
        "frames_received":         len(frames_bytes),
        "frames_with_face":        frames_with_face,
        "details": {
            "eye_counts":    blink_result["eye_counts"],
            "face_centers":  movement_result["face_centers"],
        },
    }