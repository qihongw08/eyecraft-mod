import csv
import sys
import cv2
import mediapipe as mp
import numpy as np
import jax
import jax.numpy as jnp
import pickle
from flax import linen as nn
import optax
import random

# ----------------------------
# Define model
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
# Collect 7 calibration samples (UI), auto-label 0–6
# ----------------------------
cap = cv2.VideoCapture(0)
print("📸 Collect 7 initial calibration samples automatically labeled 0–6. Press 'c' to capture each.")

calib_samples = []
calib_labels = []

while len(calib_samples) < 7:
    ret, frame = cap.read()
    if not ret:
        break
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = face_mesh.process(rgb_frame)

    if results.multi_face_landmarks:
        landmarks = results.multi_face_landmarks[0].landmark
        lm = np.array([[lm.x, lm.y, lm.z] for lm in landmarks], dtype=np.float32)
        lm_flat = lm.flatten()

        cv2.putText(frame, f"Sample {len(calib_samples)+1}/7 (label {len(calib_samples)})", (10, 40),
                    cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 255, 0), 2)

    cv2.imshow("Calibration", frame)
    key = cv2.waitKey(1) & 0xFF

    if key == ord("c") and results.multi_face_landmarks:
        # Automatically label 0 through 6
        label = len(calib_samples)
        calib_samples.append(lm_flat)
        with open("first_calib.csv", "w", newline="") as f:
            writer = csv.writer(f)
            writer.writerows([[val] for val in lm_flat.tolist()])
        calib_labels.append(label)
        print(f"Captured sample {len(calib_samples)} with label {label}")

cap.release()
cv2.destroyAllWindows()

# ----------------------------
# Duplicate to ~700 entries
# ----------------------------
times = 100  # 7*100 ≈ 700
aug_samples = []
aug_labels = []

for sample, label in zip(calib_samples, calib_labels):
    for _ in range(times):
        lm_dup = np.array([
            val + random.uniform(0, 0.03) if val > 0 else val - random.uniform(0, 0.03) if val < 0 else val
            for val in sample
        ], dtype=np.float32)
        aug_samples.append(lm_dup)
        aug_labels.append(label)

X_train = jnp.array(np.stack(aug_samples))
y_train = jnp.array(aug_labels)

print(f"Training dataset: {X_train.shape[0]} samples, {X_train.shape[1]} features")

# ----------------------------
# Initialize and train model
# ----------------------------
model = LandmarkClassifier(num_classes=7)
params = model.init(jax.random.PRNGKey(0), X_train[:1])["params"]

optimizer = optax.adam(1e-3)
opt_state = optimizer.init(params)

@jax.jit
def train_step(params, opt_state, x, y):
    def loss_fn(p):
        logits = model.apply({"params": p}, x)
        one_hot = jax.nn.one_hot(y, 7)
        loss = -jnp.mean(jnp.sum(one_hot * jax.nn.log_softmax(logits), axis=-1))
        return loss
    grads = jax.grad(loss_fn)(params)
    updates, opt_state = optimizer.update(grads, opt_state)
    new_params = optax.apply_updates(params, updates)
    return new_params, opt_state

# Simple training loop
for epoch in range(20):
    params, opt_state = train_step(params, opt_state, X_train, y_train)
    if epoch % 5 == 0:
        logits = model.apply({"params": params}, X_train)
        acc = (jnp.argmax(logits, axis=-1) == y_train).mean()
        print(f"Epoch {epoch}, training accuracy: {acc:.4f}")

# ----------------------------
# Save model
# ----------------------------
with open("/Users/tomasdavola/IdeaProjects/eyecraft-mod1/scripts/landmark_model_calibrated.pkl", "wb") as f:
    pickle.dump(params, f)

print("✅ Model trained and saved as landmark_model_calibrated.pkl")
