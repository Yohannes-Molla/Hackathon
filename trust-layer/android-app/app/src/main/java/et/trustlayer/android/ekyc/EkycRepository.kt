package et.trustlayer.android.ekyc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import et.trustlayer.android.di.TrustLayerApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EkycRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TrustLayerApi
) {
    companion object {
        private const val MAX_IMAGE_WIDTH = 1280
        private const val JPEG_QUALITY = 85
    }

    suspend fun submitVerification(
        sessionId: String,
        tenantId: String,
        idImageUri: Uri,
        selfieImageUri: Uri
    ): EkycManager.EkycResult {
        val idBytes = uriToJpegBytes(idImageUri)
        val selfieBytes = uriToJpegBytes(selfieImageUri)
        val idBody = idBytes.toRequestBody("image/jpeg".toMediaType())
        val selfieBody = selfieBytes.toRequestBody("image/jpeg".toMediaType())

        return try {
            val response = api.uploadEkycDocument(
                tenantId = tenantId,
                bearerToken = "",
                sessionId = sessionId,
                documentImage = idBody,
                livenessFrames = selfieBody
            )
            if (response.isSuccessful) {
                val body = response.body()
                EkycManager.EkycResult(
                    success = body?.status == "VERIFIED",
                    status = body?.status ?: "UNKNOWN",
                    verifiedClaims = body?.verifiedClaims
                )
            } else {
                EkycManager.EkycResult(false, "FAILED", error = "Server error: ${response.code()}")
            }
        } catch (e: Exception) {
            EkycManager.EkycResult(false, "ERROR", error = e.message)
        }
    }

    private fun uriToJpegBytes(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open input stream for $uri")
        val bytes = input.use { it.readBytes() }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        var sampleSize = 1
        while (options.outWidth / sampleSize > MAX_IMAGE_WIDTH) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
