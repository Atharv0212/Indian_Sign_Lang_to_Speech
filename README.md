# Indian_Sign_Lang_to_Speech
This repository contains the source code for an Android Studio-based Indian Sign Language to Speech Bidirectional Translator.This app aims to make a strong linguistic connection between Urban India, which has specifically focused on the increased use of 'Hinglish' as its native language, and the ISL-using community. 

## 1. Project Overview
This project aims to bridge the communication gap between the hearing/speech-impaired community and the general public by providing a seamless, real-time "Speech-to-Sign Language" and "Sign Language-to-Text/Speech" translation system, specifically focused on **Indian Sign Language (ISL)**. The application provides two distinct modes of communication: 
1. Converting spoken verbal language into visual Indian Sign Language through a slideshow of animated avatars.
2. Observing real-world ISL gestures through the device camera and predicting the corresponding text output.

## 2. Core Concepts & Technical Architecture

### 2.1. Speech-to-Sign Language (S2S) Module
The Speech-to-Sign component is designed to listen to continuous audio, transcribe it to phonetic text, and visualize it sequentially through sign language reference graphics. 

**Speech Recognition Strategy**
Initially, typical voice-to-text integration uses Google's `RecognizerIntent` API, which forces a generic Google UI dialogue overlay onto the app. To provide a seamless and customized user experience, the system instead operates on the deeper `SpeechRecognizer` Android API. This allows the application to listen and transcribe spoken language entirely in the background, keeping the user immersed in the custom application interface.

**Sentence Parsing and Sign Mapping**
Once audio is successfully transcribed to a string of text, the text is fundamentally broken down into fundamental units. Currently, the system leverages a robust fallback strategy known as *finger-spelling*, breaking down complex words into singular characters. 
- Sentences are parsed entirely into alphanumeric characters.
- Each character is matched against a hardcoded dataset of 36 distinct signs (26 English Alphabets + 10 Digits).
- For playback, the characters are sequentially mapped to avatar image resources, rendering a rapid "slideshow" effect that visually spells out the recognized speech in ISL. 

### 2.2. Sign Language-to-Text (ISL2T) Module
This module is essentially the reverse process—understanding the user's hand gestures in real-world time and predicting the character they are signing.

**Visual Data Acquisition with CameraX**
To efficiently capture frames on varied Android hardware without blocking the main workflow thread, the application leans on **CameraX**. CameraX handles the complexities of Android device hardware profiles, exposing a streamlined interface specifically tailored for real-time computer vision analysis.

**Hand Tracking Pipeline with MediaPipe**
Each frame captured by CameraX is passed to **Google's MediaPipe Hand Landmarker** model. MediaPipe is a state-of-the-art machine learning pipeline capable of highly performant geometric tracking.
- It scans the image frame for hand instances.
- Upon successful detection, it plots exactly **21 3D landmarks** (points) mapped across the hand structure (wrist, knuckles, fingertips).
- These landmarks mathematically represent the skeletal topology and posture of the user's hand independent of the device bounds or lighting conditions.

**Gesture Recognition Logic (Euclidean Distance)**
To figure out *which* letter or number the 21 generated landmarks represent, the system employs **Euclidean Distance Modeling**. In simple terms, Euclidean distance is the fundamental mathematical theorem used to calculate the straight-line distance between two points in physical (or 3D) space \( (x_2 - x_1)^2 + (y_2 - y_1)^2 + (z_2 - z_1)^2 \).

Here is exactly how the reasoning logic utilizes this math to predict sign language:
1. **Template Generation & Storage**: The app stores a pre-defined reference template dataset (like `SignTemplates.kt`). This acts as the "answer key". For every sign (A-Z, 0-9), the template stores an array of exactly 21 ideal 3D landmark coordinates representing what a perfect "A" or "B" looks like. 
2. **Real-time Normalization**: When a user holds up their hand to the camera, the app grabs their 21 live landmarks. Before comparing anything, it must "normalize" the live hand. This means shifting the coordinates so the wrist is acting as the (0,0,0) center point, and mathematically scaling the hand down so that differences in camera distance or physics (e.g. holding the phone close vs. far away, small hands vs. large hands) don't ruin the math. 
3. **Geometric Comparison (The Euclidean Loop)**: 
   - The algorithm enters a loop, testing the live, normalized hand against every single one of the 36 templates in the answer key.
   - For a single template comparison (e.g. testing if the hand is an "A"), it calculates the Euclidean distance between the live thumb tip and the template thumb tip, the live index knuckle and the template index knuckle, etc., for all 21 points. 
   - It sums up these 21 individual distances to generate a single "Error Score" for the letter "A".
4. **Prediction**: The app repeats step 3 for every template in the dataset, gathering an Error Score for all 36 possibilities. The template that yields the absolute lowest Error Score (the shortest total Euclidean aggregate distance) means its skeletal joints correspond closest to the live shape. The app registers this lowest-error template as the detected sign and outputs it to the user.

## 3. Summary
This approach ensures an on-device, responsive, and privacy-respecting architecture. By relying extensively on deterministic geometry (Euclidean matching based on 21-point skeletons) rather than black-box image classification, the algorithm maintains high frame rates necessary for mobile sign evaluation, while providing the groundwork for more complex, holistic gesture recognition (word-level signing vs. finger-spelling) in the future.
