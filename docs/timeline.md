# Project Launch Timeline: Qlcom-hack

This document provides a highly detailed six-week engineering roadmap for the development, hardware integration, optimization, and validation of the **Qlcom-hack** mobile application.

---

## 📅 Roadmap Overview

```
Week 1 [=== Foundation & Viewfinder Pipeline ===]
Week 2 [=== Block-Based DCT Watermark Engine ===]
Week 3 [=== Hardware Enclave (RSA-2048) & Room ===]
Week 4 [=== TFLite Qualcomm QNN Hardware Accel ===]
Week 5 [=== Compose HUD Refinements & Integration ===]
Week 6 [=== Validation, Benchmarking & Launch ===]
```

---

## 🛠️ Weekly Sprints & Deliverables

### Week 1: Foundation & Viewfinder Pipeline
* **Objective**: Create the repository scaffold, resolve Gradle Kotlin DSL structures, configure camera permissions, and establish the real-time live viewfinder pipeline.
* **Deliverables**:
  - Resolve root and app module Gradle configurations (`settings.gradle.kts`, `build.gradle.kts`, dependencies).
  - Declare permissions inside `AndroidManifest.xml` (Camera, granular image storage API 33+ guards).
  - Implement the CameraX `ProcessCameraProvider` configuration inside a Jetpack Compose `AndroidView` scaffold.
  - Implement a background analyzer executor pool to capture and process live frame matrices.

### Week 2: Advanced Block-Based DCT Watermark Engine
* **Objective**: Formulate the Kotlin frequency-domain block-based DCT encoding/decoding utilities, ensuring transparency and robust resistance against tampering.
* **Deliverables**:
  - Write block segmentation logic to divide frame bitmaps into 8x8 arrays.
  - Formulate forward 2D Discrete Cosine Transform (DCT) and Inverse 2D DCT (IDCT) mathematics in Kotlin.
  - Implement Quantization Index Modulation (QIM) to embed payload bits into mid-frequency (U=3, V=4) coefficients of block structures.
  - Implement block-based decoding functions to extract embedded signature byte arrays.
  - Benchmark de-quantization visual fidelity using Peak Signal-to-Noise Ratio (PSNR).

### Week 3: Hardware Enclave Cryptography (RSA-2048) & Room Database
* **Objective**: Integrate secure hardware-backed cryptographic signing using the Android KeyStore and implement the Room database media ledger.
* **Deliverables**:
  - Configure KeyStore keystore loading and generate a hardware-backed RSA-2048 signature key pair inside Qualcomm's Trusted Execution Environment (TEE).
  - Write `CryptographicSigner` utilizing `SHA256withRSA` signature schemes to sign image metadata.
  - Set up Room entities, DAO queries (Flow structures, path select functions), and the thread-safe `AppDatabase` singleton.
  - Integrate signature generation, DCT frequency injection, filesystem writing, and database logging in the camera's capture action.

### Week 4: TensorFlow Lite & Qualcomm QNN Delegate Optimizations
* **Objective**: Integrate the TensorFlow Lite framework, configure GPU delegates for Snapdragon hardware acceleration, and establish real-time inference checks.
* **Deliverables**:
  - Incorporate TFLite interpreter initialization with options supporting GPU acceleration delegate binding.
  - Set up normalizations to scale, crop, and transform live camera frames to model dimensions (e.g. 224x224 float buffers).
  - Link the real-time `ImageAnalysis.Analyzer` callback loop to execute model checks asynchronously on incoming frames.
  - Conduct hardware benchmark tests comparing CPU execution (4 threads) vs. GPU/Qualcomm QNN acceleration delegates.

### Week 5: Jetpack Compose HUD Refinements & Integration
* **Objective**: Assemble sub-screens using premium high-tech visual overlays, compile custom themes, and implement dynamic signature verification portals.
* **Deliverables**:
  - Design the cyberpunk dark-mode theme color, typography (Inter, clean monospace telemetry elements), and styles.
  - Formulate real-time scanning HUD graphics (viewfinder frames, bounding boxes, neon glowing risk gauges, latency graphs).
  - Assemble the media grid cards displaying local captures, risk ratings, and dates.
  - Build the interactive holographic verify modal displaying de-quantized DCT payloads, RSA-2048 key logs, and certified authenticity reports.

### Week 6: Validation, Benchmarking & Launch
* **Objective**: Execute end-to-end user journeys, conduct extreme stress/corruption tests on watermarked media, verify code quality, and finalize build packaging.
* **Deliverables**:
  - Perform stress tests checking watermark durability against geometric transformations (scaling, heavy JPEG compression re-saves, slight cropping).
  - Resolve unit tests checking cryptographic signature verification (`CryptographicSignerTest`).
  - Optimize memory allocation to eliminate GC allocation spikes during the frame analysis callback loop.
  - Package high-performance release APK:
    ```bash
    ./gradlew assembleRelease
    ```
