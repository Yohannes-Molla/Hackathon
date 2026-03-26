package et.trustlayer.android.ekyc

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Phase 2D — QR Code Analyzer
 *
 * CameraX ImageAnalysis.Analyzer that uses ML Kit's BarcodeScanning
 * to decode QR codes containing the Trust Layer deep link:
 *   trustlayer://register?sessionId=UUID&tenant=slug
 */
class QrCodeAnalyzer(
    private val onQrCodeScanned: (DeepLinkParams) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "QrCodeAnalyzer"
    }

    private val scanner = BarcodeScanning.getClient()
    private var isProcessing = false

    data class DeepLinkParams(
        val sessionId: String,
        val tenantSlug: String,
        val authUri: String? = null
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    if (barcode.valueType == Barcode.TYPE_URL || barcode.valueType == Barcode.TYPE_TEXT) {
                        val rawValue = barcode.rawValue ?: continue
                        val params = parseDeepLink(rawValue)
                        if (params != null) {
                            Log.i(TAG, "QR scanned: sessionId=${params.sessionId}, tenant=${params.tenantSlug}")
                            onQrCodeScanned(params)
                            return@addOnSuccessListener
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "QR scan failed", e)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    private fun parseDeepLink(uri: String): DeepLinkParams? {
        return try {
            val parsed = android.net.Uri.parse(uri)
            if (parsed.scheme != "trustlayer" || parsed.host != "register") return null

            val sessionId = parsed.getQueryParameter("sessionId") ?: return null
            val tenant = parsed.getQueryParameter("tenant") ?: "hub"
            val authUri = parsed.getQueryParameter("authUri")

            DeepLinkParams(sessionId, tenant, authUri)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse QR deep link: $uri", e)
            null
        }
    }
}
