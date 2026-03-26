package et.trustlayer.android.ekyc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import et.trustlayer.android.di.TrustLayerApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2D — eKYC Manager
 *
 * Handles the eKYC verification flow:
 *   1. Document capture (ID card / passport) → JPEG compression → upload
 *   2. 3D Liveness check (frames from CameraX) → upload
 *   3. Returns verified_claims from the eKYC backend
 */
@Singleton
class EkycManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TrustLayerApi
) {

    companion object {
        private const val TAG = "EkycManager"
        private const val MAX_IMAGE_WIDTH = 1280
        private const val JPEG_QUALITY = 85
    }

    data class EkycResult(
        val success: Boolean,
        val status: String,
        val verifiedClaims: Map<String, Any>? = null,
        val error: String? = null
    )

    /**
     * Upload a document image for eKYC verification.
     * @param sessionId The session ID from the QR code handoff
     * @param tenantId The tenant UUID
     * @param bearerToken The DPoP-bound access token
     * @param imageBytes Raw JPEG/PNG bytes from CameraX capture
     */
    suspend fun verifyDocument(
        sessionId: String,
        tenantId: String,
        bearerToken: String,
        imageBytes: ByteArray,
        livenessBytes: ByteArray? = null
    ): EkycResult {
        return try {
            val compressedImage = compressImage(imageBytes)
            val imageBody = compressedImage.toRequestBody("image/jpeg".toMediaType())
            val livenessBody = livenessBytes?.toRequestBody("application/octet-stream".toMediaType())

            val response = api.uploadEkycDocument(
                tenantId = tenantId,
                bearerToken = "DPoP $bearerToken",
                sessionId = sessionId,
                documentImage = imageBody,
                livenessFrames = livenessBody
            )

            if (response.isSuccessful) {
                val body = response.body()
                EkycResult(
                    success = body?.status == "VERIFIED",
                    status = body?.status ?: "UNKNOWN",
                    verifiedClaims = body?.verifiedClaims
                )
            } else {
                Log.e(TAG, "eKYC upload failed: ${response.code()} ${response.message()}")
                EkycResult(false, "FAILED", error = "Server error: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "eKYC verification error", e)
            EkycResult(false, "ERROR", error = e.message)
        }
    }

    /**
     * Compress and resize image to reduce upload size.
     */
    private fun compressImage(imageBytes: ByteArray): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        // Calculate sample size for downscaling
        var sampleSize = 1
        while (options.outWidth / sampleSize > MAX_IMAGE_WIDTH) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()

        return output.toByteArray()
    }
}
