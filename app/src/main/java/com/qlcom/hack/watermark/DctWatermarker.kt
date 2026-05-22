package com.qlcom.hack.watermark

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sqrt

object DctWatermarker {
    private const val BLOCK_SIZE = 8
    private const val DCT_U = 3
    private const val DCT_V = 4
    private const val QUANTIZATION_STEP = 32.0 // Balancing robustness and invisibility

    // Precomputed cosine factors for high performance
    private val cosTable = Array(BLOCK_SIZE) { x ->
        DoubleArray(BLOCK_SIZE) { u ->
            cos((2 * x + 1) * u * PI / 16.0)
        }
    }

    private fun c(w: Int): Double {
        return if (w == 0) 1.0 / sqrt(2.0) else 1.0
    }

    // Performs forward 2D DCT on an 8x8 block
    private fun forwardDCT(block: DoubleArray): DoubleArray {
        val dct = DoubleArray(BLOCK_SIZE * BLOCK_SIZE)
        for (u in 0 until BLOCK_SIZE) {
            for (v in 0 until BLOCK_SIZE) {
                var sum = 0.0
                for (x in 0 until BLOCK_SIZE) {
                    for (y in 0 until BLOCK_SIZE) {
                        val pixel = block[x * BLOCK_SIZE + y]
                        sum += pixel * cosTable[x][u] * cosTable[y][v]
                    }
                }
                dct[u * BLOCK_SIZE + v] = 0.25 * c(u) * c(v) * sum
            }
        }
        return dct
    }

    // Performs inverse 2D DCT on an 8x8 block
    private fun inverseDCT(dct: DoubleArray): DoubleArray {
        val block = DoubleArray(BLOCK_SIZE * BLOCK_SIZE)
        for (x in 0 until BLOCK_SIZE) {
            for (y in 0 until BLOCK_SIZE) {
                var sum = 0.0
                for (u in 0 until BLOCK_SIZE) {
                    for (v in 0 until BLOCK_SIZE) {
                        sum += c(u) * c(v) * dct[u * BLOCK_SIZE + v] * cosTable[x][u] * cosTable[y][v]
                    }
                }
                block[x * BLOCK_SIZE + y] = 0.25 * sum
            }
        }
        return block
    }

    // Convert string payload into bit array
    private fun stringToBits(str: String): BooleanArray {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val bits = BooleanArray(bytes.size * 8)
        for (i in bytes.indices) {
            val byteVal = bytes[i].toInt()
            for (b in 0..7) {
                bits[i * 8 + b] = ((byteVal shr (7 - b)) and 1) == 1
            }
        }
        return bits
    }

    // Convert bit array back to string payload
    private fun bitsToString(bits: BooleanArray): String {
        val bytes = ByteArray(bits.size / 8)
        for (i in bytes.indices) {
            var byteVal = 0
            for (b in 0..7) {
                if (bits[i * 8 + b]) {
                    byteVal = byteVal or (1 shl (7 - b))
                }
            }
            bytes[i] = byteVal.toByte()
        }
        return String(bytes, Charsets.UTF_8).trim { it <= ' ' }
    }

    fun embedWatermark(src: Bitmap, payload: String): Bitmap {
        val width = src.width
        val height = src.height

        // Ensure image fits complete 8x8 blocks
        val adjWidth = width - (width % BLOCK_SIZE)
        val adjHeight = height - (height % BLOCK_SIZE)

        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Retrieve pixels
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val bits = stringToBits(payload)
        var bitIndex = 0

        // Extract Luminance (Y), Cb, Cr matrices
        val yMat = DoubleArray(adjWidth * adjHeight)
        val cbMat = FloatArray(adjWidth * adjHeight)
        val crMat = FloatArray(adjWidth * adjHeight)

        for (y in 0 until adjHeight) {
            for (x in 0 until adjWidth) {
                val idx = y * width + x
                val matIdx = y * adjWidth + x
                val p = pixels[idx]
                val r = Color.red(p).toDouble()
                val g = Color.green(p).toDouble()
                val b = Color.blue(p).toDouble()

                // YCbCr conversion
                yMat[matIdx] = 0.299 * r + 0.587 * g + 0.114 * b
                cbMat[matIdx] = (-0.1687 * r - 0.3313 * g + 0.5 * b + 128.0).toFloat()
                crMat[matIdx] = (0.5 * r - 0.4187 * g - 0.0813 * b + 128.0).toFloat()
            }
        }

        // Embed bits block by block
        val block = DoubleArray(BLOCK_SIZE * BLOCK_SIZE)
        for (yBlock in 0 until (adjHeight / BLOCK_SIZE)) {
            for (xBlock in 0 until (adjWidth / BLOCK_SIZE)) {
                // Populate current 8x8 block
                for (i in 0 until BLOCK_SIZE) {
                    for (j in 0 until BLOCK_SIZE) {
                        val pxIdx = (yBlock * BLOCK_SIZE + i) * adjWidth + (xBlock * BLOCK_SIZE + j)
                        block[i * BLOCK_SIZE + j] = yMat[pxIdx]
                    }
                }

                // Forward DCT
                val dct = forwardDCT(block)

                // Embed one bit if available
                if (bitIndex < bits.size) {
                    val bit = bits[bitIndex]
                    val coeffIdx = DCT_U * BLOCK_SIZE + DCT_V
                    val coeffVal = dct[coeffIdx]

                    // Quantize and replace coefficient value
                    val temp = Math.round(coeffVal / QUANTIZATION_STEP)
                    val newTemp = if (bit) {
                        if (temp % 2 == 0L) temp + 1 else temp
                    } else {
                        if (temp % 2 != 0L) temp + 1 else temp
                    }
                    dct[coeffIdx] = newTemp * QUANTIZATION_STEP
                    bitIndex++
                }

                // Inverse DCT
                val newBlock = inverseDCT(dct)

                // Save back into Y matrix
                for (i in 0 until BLOCK_SIZE) {
                    for (j in 0 until BLOCK_SIZE) {
                        val pxIdx = (yBlock * BLOCK_SIZE + i) * adjWidth + (xBlock * BLOCK_SIZE + j)
                        yMat[pxIdx] = newBlock[i * BLOCK_SIZE + j]
                    }
                }
            }
        }

        // Reconstruct RGB and write to output bitmap
        val outPixels = pixels.clone()
        for (y in 0 until adjHeight) {
            for (x in 0 until adjWidth) {
                val idx = y * width + x
                val matIdx = y * adjWidth + x

                val yVal = yMat[matIdx]
                val cb = cbMat[matIdx] - 128.0
                val cr = crMat[matIdx] - 128.0

                // Color space conversion back to RGB
                var r = (yVal + 1.402 * cr).toInt()
                var g = (yVal - 0.34414 * cb - 0.71414 * cr).toInt()
                var b = (yVal + 1.772 * cb).toInt()

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                outPixels[idx] = Color.rgb(r, g, b)
            }
        }

        outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    fun extractWatermark(src: Bitmap, maxPayloadLength: Int): String {
        val width = src.width
        val height = src.height

        val adjWidth = width - (width % BLOCK_SIZE)
        val adjHeight = height - (height % BLOCK_SIZE)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val totalBits = maxPayloadLength * 8
        val bits = BooleanArray(totalBits)
        var bitIndex = 0

        val block = DoubleArray(BLOCK_SIZE * BLOCK_SIZE)

        for (yBlock in 0 until (adjHeight / BLOCK_SIZE)) {
            for (xBlock in 0 until (adjWidth / BLOCK_SIZE)) {
                if (bitIndex >= totalBits) break

                // Populate current 8x8 block (luminance Y only)
                for (i in 0 until BLOCK_SIZE) {
                    for (j in 0 until BLOCK_SIZE) {
                        val pxIdx = (yBlock * BLOCK_SIZE + i) * width + (xBlock * BLOCK_SIZE + j)
                        val p = pixels[pxIdx]
                        val r = Color.red(p)
                        val g = Color.green(p)
                        val b = Color.blue(p)
                        block[i * BLOCK_SIZE + j] = 0.299 * r + 0.587 * g + 0.114 * b
                    }
                }

                // Forward DCT
                val dct = forwardDCT(block)

                // Extract bit
                val coeffIdx = DCT_U * BLOCK_SIZE + DCT_V
                val coeffVal = dct[coeffIdx]
                val temp = Math.round(coeffVal / QUANTIZATION_STEP)

                bits[bitIndex] = (temp % 2 != 0L)
                bitIndex++
            }
        }

        return bitsToString(bits)
    }
}
