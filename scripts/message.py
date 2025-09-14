import cv2
import mediapipe as mp
import numpy as np
import sys
from math import atan2, degrees

mp_face_mesh = mp.solutions.face_mesh
face_mesh = mp_face_mesh.FaceMesh(
    max_num_faces=1,
    refine_landmarks=True,  # better eye/mouth tracking
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5,
)

def euclidean_dist(pt1, pt2):
    return np.linalg.norm(np.array(pt1) - np.array(pt2))


def mouth_open_ratio(landmarks, image_w, image_h):
    top_lip = landmarks[13]  # upper inner lip
    bottom_lip = landmarks[14]  # lower inner lip
    top = np.array([top_lip.x * image_w, top_lip.y * image_h])
    bottom = np.array([bottom_lip.x * image_w, bottom_lip.y * image_h])
    return euclidean_dist(top, bottom)


def eye_aspect_ratio(landmarks, indices, image_w, image_h):
    pts = [(landmarks[i].x * image_w, landmarks[i].y * image_h) for i in indices]
    A = euclidean_dist(pts[1], pts[5])
    B = euclidean_dist(pts[2], pts[4])
    C = euclidean_dist(pts[0], pts[3])
    ear = (A + B) / (2.0 * C)
    return ear

def mouth_shape_ratio(landmarks, image_w, image_h):
    # O vs E detection
    left = np.array([landmarks[61].x * image_w, landmarks[61].y * image_h])
    right = np.array([landmarks[291].x * image_w, landmarks[291].y * image_h])
    top = np.array([landmarks[13].x * image_w, landmarks[13].y * image_h])
    bottom = np.array([landmarks[14].x * image_w, landmarks[14].y * image_h])

    width = euclidean_dist(left, right)
    height = euclidean_dist(top, bottom)

    return height / width  # ratio


cap = cv2.VideoCapture(0)

EAR_THRESHOLD = 0.2
MOUTH_THRESHOLD = 15  # adjust based on camera distance

while True:
    ret, frame = cap.read()
    if not ret:
        break

    h, w, _ = frame.shape
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = face_mesh.process(rgb_frame)

    if results.multi_face_landmarks:
        landmarks = results.multi_face_landmarks[0].landmark
        mouth_ratio = mouth_open_ratio(landmarks, w, h)
        left_ear = eye_aspect_ratio(
            landmarks, [33, 160, 158, 133, 153, 144], w, h
        )  # left eye
        right_ear = eye_aspect_ratio(
            landmarks, [362, 385, 387, 263, 373, 380], w, h
        )  # right eye
        blink_left = left_ear < EAR_THRESHOLD
        blink_right = right_ear < EAR_THRESHOLD
        mouth_open = mouth_ratio > MOUTH_THRESHOLD
        mouth_ratio = mouth_open_ratio(landmarks, w, h)
        mouth_shape = mouth_shape_ratio(landmarks, w, h)

        # O mouth
        O_mouth = mouth_shape > 0.45 and mouth_open 
        # E mouth
        E_mouth = mouth_shape < 0.25 and mouth_open 

        face_data = f"{blink_left and not blink_right},{blink_right and not blink_left},{blink_left and blink_right},{O_mouth},{E_mouth}"
        display_text = f"L:{blink_left} R:{blink_right} | O:{O_mouth} E:{E_mouth}"

        sys.stdout.flush()

        cv2.putText(
            frame, display_text, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2
        )


    cv2.imshow("MediaPipe Face Tracker", frame)
    if cv2.waitKey(1) & 0xFF == 27:
        break

cap.release()
cv2.destroyAllWindows()