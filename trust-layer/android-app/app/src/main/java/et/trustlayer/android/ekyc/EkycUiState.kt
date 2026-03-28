package et.trustlayer.android.ekyc

import android.net.Uri

sealed interface EkycUiState {

    /** Step 1: Scanning QR code to get session details. */
    data object ScanQrCode : EkycUiState

    /** Step 2: Capturing or selecting an ID document image. */
    data class IdCapture(
        val sessionId: String,
        val tenantId: String
    ) : EkycUiState

    /** Step 3: Taking a live selfie. */
    data class LiveSelfie(
        val sessionId: String,
        val tenantId: String,
        val idImageUri: Uri
    ) : EkycUiState

    /** Step 4: Uploading images and waiting for verification result. */
    data class Processing(
        val sessionId: String,
        val tenantId: String,
        val idImageUri: Uri,
        val selfieImageUri: Uri
    ) : EkycUiState

    /** Step 5: Verification succeeded. */
    data object Complete : EkycUiState

    /** Error state — verification failed at any point. */
    data class Error(
        val sessionId: String?,
        val tenantId: String?,
        val message: String
    ) : EkycUiState
}
