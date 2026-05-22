package com.qlcom.hack.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.qlcom.hack.data.AppDatabase
import com.qlcom.hack.data.WatermarkRecord
import com.qlcom.hack.ml.DeepfakeDetector
import com.qlcom.hack.ui.theme.*
import com.qlcom.hack.watermark.CryptographicSigner
import com.qlcom.hack.watermark.DctWatermarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // ML Detector
    val detector = remember { DeepfakeDetector(context) }
    
    // UI State
    var deepfakeScore by remember { mutableStateOf(0.05f) }
    var latencyMs by remember { mutableStateOf(12L) }
    var isSaving by remember { mutableStateOf(false) }
    var isModelAccelerated by remember { mutableStateOf(false) }

    // Keep dynamic FPS simulation for UI aesthetic
    var currentFps by remember { mutableStateOf(30) }

    // Thread pool for analysis
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // Latest frame cache for capture snapshots
    var latestBitmapFrame by remember { mutableStateOf<Bitmap?>(null) }

    // Handle updates from scanner thread safely
    LaunchedEffect(detector) {
        isModelAccelerated = detector.isHardwareAccelerated()
    }

    // Capture/signing logic
    val onCaptureSigned = {
        val frame = latestBitmapFrame
        if (frame == null) {
            Toast.makeText(context, "Waiting for camera stream...", Toast.LENGTH_SHORT).show()
        } else {
            isSaving = true
            scope.launch(Dispatchers.Default) {
                try {
                    val currentScore = deepfakeScore
                    val timestamp = System.currentTimeMillis()
                    val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
                    
                    // 1. Create original metadata string
                    val metadata = "QlcomHack|Score:${"%.2f".format(currentScore)}|Time:$timestamp|Device:${android.os.Build.MODEL}"
                    
                    // 2. Sign metadata hash using RSA-2048 private key in KeyStore
                    val signature = CryptographicSigner.signData(metadata.toByteArray(Charsets.UTF_8))
                    
                    // 3. Compile compact DCT payload (taking subset of signature for safety space)
                    val shortSig = if (signature.length > 32) signature.substring(0, 32) else signature
                    val watermarkPayload = "$shortSig|$dateString"

                    // 4. Embed watermark payload into frequency domain (DCT) of luminance channel
                    val watermarkedBitmap = DctWatermarker.embedWatermark(frame, watermarkPayload)

                    // 5. Save the watermarked image to disk
                    val fileName = "SIGNED_$dateString.jpg"
                    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    val file = File(storageDir, fileName)
                    
                    FileOutputStream(file).use { out ->
                        watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    // 6. Log transaction record to Room database
                    val dao = database.watermarkDao()
                    val record = WatermarkRecord(
                        filePath = file.absolutePath,
                        detectionScore = currentScore,
                        signature = signature,
                        timestamp = timestamp,
                        metadataPayload = metadata
                    )
                    dao.insertRecord(record)

                    withContext(Dispatchers.Main) {
                        isSaving = false
                        Toast.makeText(
                            context, 
                            "Authentic Signature Secured! Saved in Gallery.", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        Toast.makeText(context, "Error Securing Signature: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Scanning bounding box pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "hudTransition")
    val boxAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boxAlpha"
    )

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        
        // 1. Camera Live Viewfinder
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val bitmap = imageProxy.toBitmap()
                        if (bitmap != null) {
                            // Rotate bitmap if necessary based on proxy orientation
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val finalBitmap = if (rotation != 0) {
                                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            } else {
                                bitmap
                            }
                            latestBitmapFrame = finalBitmap

                            // Execute real-time Deepfake Detector ML inference
                            val (score, inferenceTime) = detector.analyzeFrame(finalBitmap)
                            deepfakeScore = score
                            latencyMs = inferenceTime
                            
                            // Mock dynamic FPS calculation
                            currentFps = (1000 / (inferenceTime + 1).toInt()).coerceIn(15, 60)
                        }
                        imageProxy.close()
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // 2. High-Tech HUD Reticle Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxWidth = canvasWidth * 0.65f
            val boxHeight = boxWidth * 1.1f
            val left = (canvasWidth - boxWidth) / 2
            val top = (canvasHeight - boxHeight) / 2.5f

            // Draw holographic bounding box corners
            val cornerSize = 40.dp.toPx()
            val strokeWidth = 3.dp.toPx()
            val color = if (deepfakeScore > 0.5f) DeepfakeRed else CyberCyan

            // Top-Left Corner
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left, top + cornerSize)
                    lineTo(left, top)
                    lineTo(left + cornerSize, top)
                },
                color = color.copy(alpha = boxAlpha),
                style = Stroke(width = strokeWidth)
            )

            // Top-Right Corner
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left + boxWidth - cornerSize, top)
                    lineTo(left + boxWidth, top)
                    lineTo(left + boxWidth, top + cornerSize)
                },
                color = color.copy(alpha = boxAlpha),
                style = Stroke(width = strokeWidth)
            )

            // Bottom-Left Corner
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left, top + boxHeight - cornerSize)
                    lineTo(left, top + boxHeight)
                    lineTo(left + cornerSize, top + boxHeight)
                },
                color = color.copy(alpha = boxAlpha),
                style = Stroke(width = strokeWidth)
            )

            // Bottom-Right Corner
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left + boxWidth - cornerSize, top + boxHeight)
                    lineTo(left + boxWidth, top + boxHeight)
                    lineTo(left + boxWidth, top + boxHeight - cornerSize)
                },
                color = color.copy(alpha = boxAlpha),
                style = Stroke(width = strokeWidth)
            )
        }

        // 3. Floating Telemetry Widgets
        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(DarkSurface.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "SYSTEM STATUS: SECURE",
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "CORE LATENCY: ${latencyMs}ms",
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "PROC SPEED: ${currentFps} FPS",
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isModelAccelerated) AuthenticGreen else WarningOrange)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isModelAccelerated) "ACCEL: Qualcomm QNN (GPU)" else "ACCEL: CPU (Fallback)",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 4. Real-time Risk Level indicator
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (deepfakeScore > 0.5f) 
                            listOf(DeepfakeRed.copy(alpha = 0.9f), Color(0xFF8B0000).copy(alpha = 0.9f))
                        else 
                            listOf(AuthenticGreen.copy(alpha = 0.9f), Color(0xFF006400).copy(alpha = 0.9f))
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (deepfakeScore > 0.5f) "FAKE DETECTED" else "AUTHENTIC",
                color = if (deepfakeScore > 0.5f) Color.White else DarkBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 5. Futuristic Bottom HUD Panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, DarkBackground.copy(alpha = 0.95f), DarkBackground)
                    )
                )
                .padding(24.dp)
        ) {
            // Analytical Risk Gauge Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEEPFAKE ANALYSIS RISK GAUGE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${(deepfakeScore * 100).toInt()}%",
                    color = if (deepfakeScore > 0.5f) DeepfakeRed else CyberCyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Linear glowing progress indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkSurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(deepfakeScore)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(AuthenticGreen, WarningOrange, DeepfakeRed)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary telemetry button
                OutlinedButton(
                    onClick = {
                        val method = if (isModelAccelerated) "Qualcomm GPU (QNN Delegate)" else "Host CPU"
                        Toast.makeText(context, "Active Engine: TensorFlow Lite | Acceleration: $method", Toast.LENGTH_LONG).show()
                    },
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info, 
                        contentDescription = "Engine info",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ENGINE INFO", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                // Core Cryptographic Signature Capture Button
                Button(
                    onClick = onCaptureSigned,
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = TextDark
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .width(200.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(listOf(CyberCyan, NeonPurple, CyberCyan)),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = TextDark,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle, 
                            contentDescription = "Sign icon"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SECURE & SIGN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// Convert ImageProxy to Bitmap helper
private fun ImageProxy.toBitmap(): Bitmap? {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return try {
        // Since we output RGBA_8888, reconstruct the pixels directly
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        bitmap
    } catch (e: Exception) {
        null
    }
}
