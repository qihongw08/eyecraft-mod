import sys

import cv2
import mediapipe as mp
import numpy as np
import jax
import jax.numpy as jnp
import pickle
from flax import linen as nn

# ----------------------------
# Define the same model
# ----------------------------
class LandmarkClassifier(nn.Module):
    num_classes: int = 7

    @nn.compact
    def __call__(self, x):
        x = nn.Dense(512)(x)
        x = nn.relu(x)
        x = nn.Dense(256)(x)
        x = nn.relu(x)
        x = nn.Dense(self.num_classes)(x)
        return x

# ----------------------------
# Load trained model
# ----------------------------
with open("/Users/qihongwu/Downloads/EyeCraft/scripts/landmark_model_calibrated.pkl", "rb") as f:
    params = pickle.load(f)

model = LandmarkClassifier(num_classes=7)

# ----------------------------
# Mediapipe setup
# ----------------------------
mp_face_mesh = mp.solutions.face_mesh
face_mesh = mp_face_mesh.FaceMesh(
    max_num_faces=1,
    refine_landmarks=True,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5,
)

# ----------------------------
# Video capture
# ----------------------------
cap = cv2.VideoCapture(0)

print("✅ Running inference. Press 'q' to quit.")

while True:
    ret, frame = cap.read()
    if not ret:
        break

    h, w, _ = frame.shape
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = face_mesh.process(rgb_frame)

    pred_label = None

    if results.multi_face_landmarks:
        landmarks = results.multi_face_landmarks[0].landmark
        lm = np.array([[lm.x, lm.y, lm.z] for lm in landmarks], dtype=np.float32)
        lm_flat = lm.flatten()[None, :]  # shape (1, 468*3)

        # JAX prediction
        logits = model.apply({"params": params}, jnp.array(lm_flat))
        pred_label = int(jnp.argmax(logits, axis=-1)[0])

        # Draw prediction on frame
        cv2.putText(frame, f"Pred: {pred_label}", (10, 40),
                    cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 255, 0), 2)
        print(pred_label)
        sys.stdout.flush()

    cv2.imshow("Landmark Inference", frame)

    if cv2.waitKey(1) & 0xFF == ord("q"):
        break

cap.release()
cv2.destroyAllWindows()
