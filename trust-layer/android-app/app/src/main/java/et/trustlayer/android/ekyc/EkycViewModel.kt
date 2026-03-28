package et.trustlayer.android.ekyc

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EkycViewModel @Inject constructor(
    private val repository: EkycRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EkycUiState>(EkycUiState.ScanQrCode)
    val uiState: StateFlow<EkycUiState> = _uiState.asStateFlow()

    fun onQrCodeScanned(sessionId: String, tenantId: String) {
        _uiState.value = EkycUiState.IdCapture(sessionId, tenantId)
    }

    fun onIdCaptured(imageUri: Uri) {
        val current = _uiState.value as? EkycUiState.IdCapture ?: return
        _uiState.value = EkycUiState.LiveSelfie(
            sessionId = current.sessionId,
            tenantId = current.tenantId,
            idImageUri = imageUri
        )
    }

    fun onSelfieCaptured(imageUri: Uri) {
        val current = _uiState.value as? EkycUiState.LiveSelfie ?: return
        val processing = EkycUiState.Processing(
            sessionId = current.sessionId,
            tenantId = current.tenantId,
            idImageUri = current.idImageUri,
            selfieImageUri = imageUri
        )
        _uiState.value = processing
        startVerification(processing)
    }

    private fun startVerification(processing: EkycUiState.Processing) {
        viewModelScope.launch {
            delay(3000) // Simulate network latency

            val result = try {
                repository.submitVerification(
                    sessionId = processing.sessionId,
                    tenantId = processing.tenantId,
                    idImageUri = processing.idImageUri,
                    selfieImageUri = processing.selfieImageUri
                )
            } catch (e: Exception) {
                EkycManager.EkycResult(false, "ERROR", error = e.message)
            }

            if (result.success) {
                _uiState.value = EkycUiState.Complete
            } else {
                _uiState.value = EkycUiState.Error(
                    sessionId = processing.sessionId,
                    tenantId = processing.tenantId,
                    message = result.error ?: "Verification failed"
                )
            }
        }
    }

    fun restart() {
        val current = _uiState.value
        // If we have a valid session, go back to IdCapture; otherwise back to QR scan
        if (current is EkycUiState.Error && current.sessionId != null && current.tenantId != null) {
            _uiState.value = EkycUiState.IdCapture(current.sessionId, current.tenantId)
        } else {
            _uiState.value = EkycUiState.ScanQrCode
        }
    }

    fun resetToScan() {
        _uiState.value = EkycUiState.ScanQrCode
    }
}
