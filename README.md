# Qlcom-hack: Real-Time Deepfake Detection Camera with Cryptographic DCT Watermarking

**Qlcom-hack** is a premium, high-tech Android camera utility designed to combat synthetic media (deepfakes) at the point of capture. By marrying real-time **TensorFlow Lite convolutional neural networks** (optimized for **Qualcomm QNN hardware acceleration**) with robust **Discrete Cosine Transform (DCT) spatial-frequency watermarking** and **RSA-2048 cryptographic signing**, the application guarantees absolute media provenance and anti-tamper security.

---

## 🌟 Key Features

1. **Real-Time Deepfake Scanning HUD**:
   - Stream live camera frames at target resolutions using **CameraX**.
   - Asynchronously analyze facial feature meshes and color channel jitter via custom **TensorFlow Lite** interpreters.
   - Designed to bind directly to Snapdragon platforms using **Qualcomm QNN SDK GPU Delegates** for sub-15ms inference latency.
   - Cyberpunk HUD overlays show glowing risk indicators, active FPS readings, and accelerator telemetry.

2. **Cryptographic Secure Capture**:
   - Creates/restores hardware-backed **RSA-2048 Key Pairs** inside the **Android KeyStore** (backed by Qualcomm Trusted Execution Environment / Secure Enclave).
   - Cryptographically signs captured frame hashes + local telemetry (timestamp, risk score, device model) using standard `SHA256withRSA` signature schemes.

3. **Spatial-Frequency Watermarking (DCT)**:
   - Converts pixels to YCbCr space and divides the luminance ($Y$) channel into 8x8 block structures.
   - Computes forward 2D **Discrete Cosine Transform (DCT)** for each block.
   - Embeds digital signatures into mid-frequency coefficients using **Quantization Index Modulation (QIM)** to preserve perfect visual transparency while ensuring high resistance against compression, resizing, or cropping.
   - Performs Inverse DCT (IDCT) to reconstruct high-fidelity signed Bitmaps.

4. **Media Ledger & Extraction verification Panel**:
   - An on-device Room database stores transaction logs, deepfake scores, file coordinates, and RSA signatures.
   - The interactive gallery tab allows users to select signed images and run an **on-device DCT decoder extraction** to verify the cryptographic digital signature, instantly reporting whether the media is authentic or has been tampered with.

---

## 🛠️ Architecture & Tech Stack

```
                                    +------------------------------------------+
                                    |         CameraX Live Viewfinder          |
                                    +--------------------+---------------------+
                                                         |
                                                (Analyze Frames)
                                                         v
                                    +------------------------------------------+
                                    |     TFLite Engine + Qualcomm QNN Delegate|
                                    +--------------------+---------------------+
                                                         |
                                                (Classify Spoof Risk)
                                                         v
                                    +------------------------------------------+
                                    |        Interactive HUD Reticle           |
                                    +--------------------+---------------------+
                                                         |
                                                 (Secure Capture)
                                                         v
     +-------------------+          +--------------------+---------------------+
     |  Android KeyStore | -------> |       RSA-2048 Cryptographic Signer      |
     | (Qualcomm TEE/SE) |          +--------------------+---------------------+
     +-------------------+                               | (Sign SHA-256 Hash)
                                                         v
                                    +------------------------------------------+
                                    |      2D Discrete Cosine Transform        |
                                    |        (DCT Frequency Embedder)          |
                                    +--------------------+---------------------+
                                                         | (Write Image to disk)
                                                         v
     +-------------------+          +--------------------+---------------------+
     |   Room Database   | <------- |           Signed Image Gallery           |
     |  (Media Ledger)   |          +------------------------------------------+
     +-------------------+
```

- **Language**: Kotlin with Kotlin DSL Build Scripts (`.gradle.kts`)
- **UI Framework**: Jetpack Compose (Material 3) with custom cyberpunk slate & neon themes
- **Jetpack Libraries**: CameraX (Preview, ImageAnalysis), Room (KTX, Compiler)
- **ML Delegate Platform**: TensorFlow Lite Runtime + GPU Delegates
- **Security Utilities**: Java Security KeyStore, standard Base64

---

## 📂 Directory Layout

```
Qlcom-hack/
├── app/
│   ├── build.gradle.kts              # Application module dependencies & configurations
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml   # Camera, storage, and hardware permissions
│           ├── java/com/qlcom/hack/
│           │   ├── MainActivity.kt   # Dynamic Permission Guard & Tab Navigator
│           │   ├── data/             # Persistent Room database storage layers
│           │   │   ├── WatermarkRecord.kt
│           │   │   ├── WatermarkDao.kt
│           │   │   └── AppDatabase.kt
│           │   ├── ml/               # TensorFlow Lite model handler & CPU/GPU delegates
│           │   │   └── DeepfakeDetector.kt
│           │   ├── ui/               # Modular Compose Screen modules & Themes
│           │   │   ├── theme/        # High-tech Cyberpunk dark-mode palette
│           │   │   │   ├── Color.kt
│           │   │   │   ├── Theme.kt
│           │   │   │   └── Type.kt
│           │   │   └── screens/      # Viewfinders and Verifiers
│           │   │       ├── CameraScreen.kt
│           │   │       └── GalleryScreen.kt
│           │   └── watermark/        # Block-based DCT embedding & RSA signing
│           │       ├── DctWatermarker.kt
│           │       └── CryptographicSigner.kt
│           └── res/                  # App icon, themes and backup policies
├── models/                           # Snapdragon GPU delegate binaries & models
│   └── deepfake_detector.tflite      # Target deepfake classifier path
├── docs/
│   └── timeline.md                   # Six-week project launch timeline
├── build.gradle.kts                  # Root Gradle compilation settings
└── settings.gradle.kts               # Module declarations
```

---

## 🚀 Setup & Build Instructions

### Prerequisites
1. **Android Studio Jellyfish (2023.3.1+)** or newer.
2. **Android SDK 34** (Platform and Build-Tools).
3. A physical Qualcomm Snapdragon-powered Android device (recommended for QNN GPU acceleration).

### Compilation
1. Clone the project or open the workspace folder `Qlcom-hack/` in Android Studio.
2. Android Studio will automatically resolve Gradle wrapper properties and download Compose, CameraX, Room, and TensorFlow Lite libraries.
3. Sync project files with Gradle.
4. **Deploying the ML Model**:
   - Place your compiled `deepfake_detector.tflite` model file under `models/` or copy it to the app's standard assets/internal files folder (e.g. `filesDir/models/deepfake_detector.tflite`).
   - If no model file is loaded, the engine automatically runs in a high-fidelity **Simulator Mode** executing real-time color channel variance telemetry, ensuring the camera remains fully testable without requiring hardcoded binary files.
5. Hit **Run** or build/assemble using Gradle CLI:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🧪 Verification & Operational Flow

1. **Launch App**:
   - The permission screen requests **Camera access** and **Image Storage** permission.
2. **Scanner HUD View**:
   - The screen renders a live viewfinder. A glowing grid boundary flashes over subjects.
   - Top panels show core telemetry: Active inference speed (in ms), frame frame-rate (FPS), and Qualcomm QNN acceleration modes.
3. **Capture Image**:
   - Click the glowing central button `SECURE & SIGN`.
   - The app signs the image's hash metadata using RSA-2048, encodes it inside the block-based 8x8 DCT frequencies of the luminance layer, outputs the watermarked JPEG to private storage, and writes the signed transaction to Room.
4. **Verification Ledger**:
   - Switch to the `LEDGER` tab to view the captures.
   - Click `VERIFY WATERMARK` on any image. An overlay dialog extracts the block-frequency bits, decodes the signature metadata, validates the signature cryptographically, and outputs a certified verification notice.
