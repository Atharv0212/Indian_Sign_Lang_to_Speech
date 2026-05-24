import cv2
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
import os
import sys
import math

def normalize_landmarks(landmarks):
    # Wrist is landmark 0
    wrist = landmarks[0]
    
    # Translation normalization: shift everything so wrist is at (0, 0, 0)
    shifted = []
    for lm in landmarks:
        shifted.append({
            'x': lm.x - wrist.x,
            'y': lm.y - wrist.y,
            'z': lm.z - wrist.z
        })
    
    # Scale normalization: find distance from wrist to middle finger MCP (landmark 9)
    mcp = shifted[9]
    scale = math.sqrt(mcp['x']**2 + mcp['y']**2 + mcp['z']**2)
    
    if scale == 0:
        scale = 1.0 # fallback

    normalized = []
    for lm in shifted:
        normalized.append({
            'x': lm['x'] / scale,
            'y': lm['y'] / scale,
            'z': lm['z'] / scale
        })
        
    return normalized

def main():
    if len(sys.argv) < 2:
        print("Usage: python extract_landmarks.py <path_to_images_folder>")
        sys.exit(1)
        
    folder_path = sys.argv[1]
    
    if not os.path.isdir(folder_path):
        print(f"Error: {folder_path} is not a valid directory.")
        sys.exit(1)

    # Path to the task model we already downloaded for Android
    model_path = os.path.join("app", "src", "main", "assets", "hand_landmarker.task")
    if not os.path.exists(model_path):
        print(f"Error: Could not find {model_path}. Make sure you run this script from the project root.")
        sys.exit(1)

    # Initialize the modern HandLandmarker Task API
    base_options = python.BaseOptions(model_asset_path=model_path)
    options = vision.HandLandmarkerOptions(
        base_options=base_options, 
        running_mode=vision.RunningMode.IMAGE,
        num_hands=2,
        min_hand_detection_confidence=0.5
    )
    
    templates = {}
    import re

    with vision.HandLandmarker.create_from_options(options) as detector:
        for filename in os.listdir(folder_path):
            if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
                # Extract label by removing any trailing numbers/separators (e.g. A_1.jpg -> A, a.1.jpg -> A, b,1.jpg -> B)
                base_name = os.path.splitext(filename)[0]
                label = re.sub(r'[_.,\-]\d+$', '', base_name).upper() # Normalize to uppercase
                
                file_path = os.path.join(folder_path, filename)
                
                # Use mediapipe's native image load
                mp_image = mp.Image.create_from_file(file_path)
                result = detector.detect(mp_image)
                
                if not result.hand_landmarks:
                    print(f"Warning: No hands detected in {filename}")
                    continue
                left_hand = [{'x': 0.0, 'y': 0.0, 'z': 0.0}] * 21
                right_hand = [{'x': 0.0, 'y': 0.0, 'z': 0.0}] * 21
                
                for i, hand_landmarks in enumerate(result.hand_landmarks):
                    category = result.handedness[i][0].category_name
                    norm_lms = normalize_landmarks(hand_landmarks)
                    if category == 'Left':
                        left_hand = norm_lms
                    else:
                        right_hand = norm_lms
                
                combined_lms = left_hand + right_hand
                
                if label not in templates:
                    templates[label] = []
                templates[label].append(combined_lms)
                print(f"Successfully processed {filename} as label {label}")

    if not templates:
        print("No templates generated.")
        sys.exit(1)

    # Average the landmarks for each label
    averaged_templates = {}
    for label, lms_list in templates.items():
        num_samples = len(lms_list)
        avg_lms = []
        for i in range(42): # 21 for Left, 21 for Right = 42 landmarks total
            avg_x = sum(lms[i]['x'] for lms in lms_list) / num_samples
            avg_y = sum(lms[i]['y'] for lms in lms_list) / num_samples
            avg_z = sum(lms[i]['z'] for lms in lms_list) / num_samples
            avg_lms.append({'x': avg_x, 'y': avg_y, 'z': avg_z})
        averaged_templates[label] = avg_lms
        print(f"Averaged {num_samples} samples for label {label}")

    # Generate Kotlin Code
    kotlin_code = "package com.example.speechtotextapp\n\n"
    kotlin_code += "object SignTemplates {\n"
    kotlin_code += "    // Map of Character Label -> List of 42 3D coordinates (x, y, z) [126 floats]\n"
    kotlin_code += "    val templates = mapOf<String, FloatArray>(\n"
    
    # Iterate using averaged_templates
    items = list(averaged_templates.items())
    for i, (label, lms) in enumerate(items):
        flat_coords = []
        for lm in lms:
            flat_coords.extend([lm['x'], lm['y'], lm['z']])
            
        coords_str = ", ".join(f"{v}f" for v in flat_coords)
        kotlin_code += f"        \"{label}\" to floatArrayOf({coords_str})"
        if i < len(items) - 1:
            kotlin_code += ",\n"
        else:
            kotlin_code += "\n"
            
    kotlin_code += "    )\n}\n"
    
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "SignTemplates.kt")
    with open(output_path, "w") as f:
        f.write(kotlin_code)
        
    print(f"\nDone! Generated {output_path}")
    print("Move this file to your Android app's source directory (app/src/main/java/com/example/speechtotextapp/)")

if __name__ == "__main__":
    main()
