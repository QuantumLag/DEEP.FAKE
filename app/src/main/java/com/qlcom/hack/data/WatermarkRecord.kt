package com.qlcom.hack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watermark_records")
data class WatermarkRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val detectionScore: Float, // Deepfake prediction score (0.0 to 1.0)
    val signature: String,     // Base64 RSA-2048 cryptographic signature
    val timestamp: Long,       // Capture epoch time
    val metadataPayload: String // Raw metadata string that was DCT embedded
)
