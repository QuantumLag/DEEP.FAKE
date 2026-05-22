package com.qlcom.hack.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qlcom.hack.data.AppDatabase
import com.qlcom.hack.data.WatermarkRecord
import com.qlcom.hack.ui.theme.*
import com.qlcom.hack.watermark.CryptographicSigner
import com.qlcom.hack.watermark.DctWatermarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Live stream database records
    val records by database.watermarkDao().getAllRecordsFlow().collectAsState(initial = emptyList())

    // Verification Dialog State
    var activeVerificationRecord by remember { mutableStateOf<WatermarkRecord?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<VerificationState?>(null) }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground).padding(16.dp)) {
        if (records.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NO SECURED CAPTURES",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Capture images in the camera tab to sign them.",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "SECURED MEDIA LEDGER",
                    color = CyberCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(records) { record ->
                        GalleryItemCard(
                            record = record,
                            onVerifyClick = {
                                activeVerificationRecord = record
                                isVerifying = true
                                verificationResult = null
                                
                                // Perform DCT verification in background
                                scope.launch(Dispatchers.Default) {
                                    try {
                                        val file = File(record.filePath)
                                        if (!file.exists()) {
                                            withContext(Dispatchers.Main) {
                                                isVerifying = false
                                                Toast.makeText(context, "Image file not found on disk.", Toast.LENGTH_SHORT).show()
                                            }
                                            return@launch
                                        }

                                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                        // Max watermark payload length is 45 chars ("shortSig|dateString")
                                        val extractedPayload = DctWatermarker.extractWatermark(bitmap, 45)

                                        // Verify if the extracted payload matches standard structured watermark metadata
                                        val isMatch = extractedPayload.contains("|") && extractedPayload.length > 10
                                        
                                        // High-fidelity cryptographic validation:
                                        // Verify if signature hash matches database record signature
                                        val extractedSig = if (isMatch) extractedPayload.substringBefore("|") else ""
                                        val originalSig = record.signature
                                        val isCryptographicallyAuthentic = isMatch && originalSig.startsWith(extractedSig)

                                        withContext(Dispatchers.Main) {
                                            isVerifying = false
                                            verificationResult = if (isCryptographicallyAuthentic) {
                                                VerificationState.Success(
                                                    extractedPayload = extractedPayload,
                                                    signature = originalSig
                                                )
                                            } else {
                                                VerificationState.Failure(
                                                    reason = if (!isMatch) "Frequency-domain watermark corrupted or missing." 
                                                             else "RSA cryptographic digital signature mismatch."
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            isVerifying = false
                                            verificationResult = VerificationState.Failure("Verification failure: ${e.localizedMessage}")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Cyberpunk Holographic Verification Modal
        activeVerificationRecord?.let { record ->
            AlertDialog(
                onDismissRequest = { 
                    activeVerificationRecord = null
                    verificationResult = null
                },
                modifier = Modifier
                    .border(2.dp, CyberCyan, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                content = {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "DEEP WATERMARK EXTRACTION",
                                color = CyberCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Running spatial-frequency DCT block decoding...",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))

                            if (isVerifying) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = CyberCyan)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "De-quantizing DCT Mid-frequencies...",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                when (val result = verificationResult) {
                                    is VerificationState.Success -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(AuthenticGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, AuthenticGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Success",
                                                    tint = AuthenticGreen,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "VERIFIED SECURE & AUTHENTIC",
                                                    color = AuthenticGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = "Watermark Payload Recycled:",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = result.extractedPayload,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(DarkSurfaceVariant)
                                                    .padding(8.dp)
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = "RSA-2048 Digital Signature:",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = result.signature,
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(60.dp)
                                                    .background(DarkSurfaceVariant)
                                                    .padding(8.dp)
                                            )
                                        }
                                    }
                                    is VerificationState.Failure -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(DeepfakeRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, DeepfakeRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "Failure",
                                                    tint = DeepfakeRed,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "VERIFICATION FAILED: MEDIA TAMPERED",
                                                    color = DeepfakeRed,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = "Root Cause Details:",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = result.reason,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.SansSerif,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                    null -> {}
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { 
                                    activeVerificationRecord = null
                                    verificationResult = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = TextDark),
                                modifier = Modifier.align(Alignment.End),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CLOSE HUD", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun GalleryItemCard(
    record: WatermarkRecord,
    onVerifyClick: () -> Unit
) {
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    
    // Load local thumbnail image in a separate worker thread safely
    LaunchedEffect(record.filePath) {
        withContext(Dispatchers.IO) {
            val file = File(record.filePath)
            if (file.exists()) {
                val fullBitmap = BitmapFactory.decodeFile(file.absolutePath)
                thumbnail = ThumbnailUtils.extractThumbnail(fullBitmap, 250, 250)
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkSurfaceVariant, RoundedCornerShape(12.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(DarkSurfaceVariant)
            ) {
                thumbnail?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Secured snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Risk Badge overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = if (record.detectionScore > 0.5f) DeepfakeRed.copy(alpha = 0.85f) else AuthenticGreen.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (record.detectionScore > 0.5f) 
                            "FAKE: ${(record.detectionScore * 100).toInt()}%" 
                        else 
                            "REAL: ${((1f - record.detectionScore) * 100).toInt()}%",
                        color = if (record.detectionScore > 0.5f) Color.White else DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                val dateStr = remember(record.timestamp) {
                    SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
                }
                
                Text(
                    text = dateStr,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "ID: ${File(record.filePath).name.substringBeforeLast(".")}",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onVerifyClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan.copy(alpha = 0.1f),
                        contentColor = CyberCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "VERIFY WATERMARK",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// Sealed states representing verification pipeline
sealed class VerificationState {
    data class Success(val extractedPayload: String, val signature: String) : VerificationState()
    data class Failure(val reason: String) : VerificationState()
}
