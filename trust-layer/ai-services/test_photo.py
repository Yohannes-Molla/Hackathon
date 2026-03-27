import cv2
import os

# --- Load full ID image ---
image_path = "sample_id.jpg"  # Replace with your scanned ID
img = cv2.imread(image_path)

# --- Convert to grayscale for face detection ---
gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

# --- Load OpenCV pre-trained face detector ---
face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")

# --- Detect faces ---
faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(50, 50))

if len(faces) == 0:
    print("No face detected in the ID image.")
else:
    # Optionally pick the largest face (if multiple faces detected)
    faces = sorted(faces, key=lambda f: f[2]*f[3], reverse=True)
    x, y, w, h = faces[0]

    # --- Add padding to capture full head + neck ---
    pad_y_top = int(h * 0.25)   # extend 25% above
    pad_y_bottom = int(h * 0.25)  # extend 25% below
    pad_x = int(w * 0.1)        # extend 10% left and right

    x_new = max(0, x - pad_x)
    y_new = max(0, y - pad_y_top)
    w_new = min(img.shape[1] - x_new, w + 2*pad_x)
    h_new = min(img.shape[0] - y_new, h + pad_y_top + pad_y_bottom)

    # Crop the padded face area
    id_photo = img[y_new:y_new+h_new, x_new:x_new+w_new]

    # --- Save the extracted ID photo ---
    save_path = "extracted_id_photo.jpg"
    cv2.imwrite(save_path, id_photo)
    print(f"ID photo automatically extracted and saved as {save_path}")

    # --- Display the extracted photo ---
    cv2.imshow("Extracted ID Photo", id_photo)
    cv2.waitKey(0)
    cv2.destroyAllWindows()