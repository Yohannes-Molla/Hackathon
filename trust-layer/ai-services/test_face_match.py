import os
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
import cv2
import numpy as np
from deepface import DeepFace

# -----------------------------
# Paths
# -----------------------------
ID_PHOTO      = "extracted_id_photo.jpg"   # extracted from the ID card
SAMPLE_PHOTO1 = "sample_photo1.jpg"        # selfie / live photo 1 (expected: SAME person)
SAMPLE_PHOTO2 = "sample_photo2.jpg"        # selfie / live photo 2 (expected: DIFFERENT person)

# -----------------------------
# DeepFace settings
# -----------------------------
# MODEL options (in order of accuracy):
#   "Facenet512"  ← best accuracy,  ~90MB download
#   "ArcFace"     ← great accuracy, ~70MB download
#   "Facenet"     ← good,           ~90MB download
#   "VGG-Face"    ← older,          ~550MB download  (avoid for hackathon)
MODEL    = "Facenet512"

# DETECTOR options:
#   "opencv"      ← fastest, already installed, good enough
#   "retinaface"  ← most accurate detector, slower, extra install
#   "mtcnn"       ← accurate, slower, extra install
DETECTOR = "opencv"

# METRIC options: "cosine", "euclidean", "euclidean_l2"
METRIC   = "cosine"

# DeepFace sets its own threshold per model+metric internally.
# We use its built-in threshold (result["verified"]) but also
# show the raw distance so you can tune it later.

# -----------------------------
# Helper: debug face detection
# Draws bounding box and saves annotated image
# -----------------------------
def debug_face_detection(image_path):
    img = cv2.imread(image_path)
    if img is None:
        print(f"  ❌ Cannot read: {image_path}")
        return

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    face_cascade = cv2.CascadeClassifier(
        cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
    )
    faces = face_cascade.detectMultiScale(
        gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30)
    )

    if len(faces) == 0:
        print(f"  ⚠️  No face detected in: {image_path}")
    else:
        for (x, y, w, h) in faces:
            cv2.rectangle(img, (x, y), (x+w, y+h), (0, 255, 0), 2)
        out_path = f"debug_face_{os.path.basename(image_path)}"
        cv2.imwrite(out_path, img)
        print(f"  ✅ {len(faces)} face(s) detected → debug saved: {out_path}")

# -----------------------------
# Helper: compare two photos
# -----------------------------
def verify_faces(path1, path2, label=""):
    print(f"\n{'='*55}")
    print(f"🔍  {os.path.basename(path1)}  ←→  {os.path.basename(path2)}")
    if label:
        print(f"    {label}")
    print(f"{'='*55}")

    for path in [path1, path2]:
        if not os.path.exists(path):
            print(f"  ❌ File not found: {path}")
            return None

    try:
        result = DeepFace.verify(
            img1_path        = path1,
            img2_path        = path2,
            model_name       = MODEL,
            detector_backend = DETECTOR,
            distance_metric  = METRIC,
            enforce_detection = True   # set False if face detection keeps failing
        )

        distance   = result["distance"]
        threshold  = result["threshold"]
        verified   = result["verified"]
        # Convert cosine distance → similarity percentage
        # cosine distance 0 = identical, 1 = opposite
        # so similarity = (1 - distance) * 100
        similarity_pct = round((1 - distance) * 100, 2)

        print(f"  Model      : {MODEL}")
        print(f"  Detector   : {DETECTOR}")
        print(f"  Metric     : {METRIC}")
        print(f"  Distance   : {distance:.4f}  (threshold: {threshold})")
        print(f"  Similarity : {similarity_pct}%")

        if verified:
            print(f"  ✅ MATCH — Same person")
        else:
            print(f"  ❌ NO MATCH — Different person")

        return {
            "img1":           path1,
            "img2":           path2,
            "distance":       distance,
            "threshold":      threshold,
            "similarity_pct": similarity_pct,
            "verified":       verified
        }

    except ValueError as e:
        # Usually means face not detected in one of the images
        print(f"  ⚠️  Face detection failed: {e}")
        print("      → Try setting enforce_detection=False at line 88")
        print("      → Or check debug_face_*.jpg to see what the detector sees")
        return None

    except Exception as e:
        print(f"  ❌ Unexpected error: {e}")
        return None

# -----------------------------
# Step 1: debug face detection on all photos first
# -----------------------------
print("🔎 Checking face detection on all photos...")
for photo in [ID_PHOTO, SAMPLE_PHOTO1, SAMPLE_PHOTO2]:
    print(f"\n  [{photo}]")
    if os.path.exists(photo):
        debug_face_detection(photo)
    else:
        print(f"  ❌ File not found: {photo}")

# -----------------------------
# Step 2: run face matching
# NOTE: first run will download the Facenet512 model (~90MB)
#       subsequent runs load it from cache instantly
#       cached at: C:\Users\derej\.deepface\weights\
# -----------------------------
print(f"\n⏳ Running face matching with {MODEL}...")
print("   (First run downloads the model — this may take a minute)\n")

results = []

r1 = verify_faces(
    ID_PHOTO, SAMPLE_PHOTO1,
    label="ID card face  vs  Photo 1  (expected: ✅ MATCH)"
)
if r1: results.append(r1)

r2 = verify_faces(
    ID_PHOTO, SAMPLE_PHOTO2,
    label="ID card face  vs  Photo 2  (expected: ❌ NO MATCH)"
)
if r2: results.append(r2)

r3 = verify_faces(
    SAMPLE_PHOTO1, SAMPLE_PHOTO2,
    label="Photo 1  vs  Photo 2  (cross-check)"
)
if r3: results.append(r3)

# -----------------------------
# Step 3: summary table
# -----------------------------
print(f"\n{'='*55}")
print("📊  SUMMARY")
print(f"{'='*55}")
print(f"  {'Comparison':<42} {'Similarity':>10}  Result")
print(f"  {'-'*42}  {'-'*10}  {'-'*10}")
for r in results:
    label   = f"{os.path.basename(r['img1'])} ↔ {os.path.basename(r['img2'])}"
    verdict = "✅ MATCH" if r["verified"] else "❌ NO MATCH"
    print(f"  {label:<42} {r['similarity_pct']:>9.2f}%  {verdict}")

print(f"\n  Model used  : {MODEL}")
print(f"  Detector    : {DETECTOR}")
print(f"  Metric      : {METRIC}")
print(f"  Model cache : C:\\Users\\derej\\.deepface\\weights\\")