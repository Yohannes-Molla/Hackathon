package et.trustlayer.android.biometric

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Phase 2C — BiometricPromptManager
 *
 * Wraps BiometricPrompt with:
 *   - CryptoObject support for hardware-backed signing
 *   - Class 3 (BIOMETRIC_STRONG) enforcement
 *   - Comprehensive error handling (lockout, no biometrics, HW unavailable)
 *   - Suspending coroutine API for clean Kotlin integration
 */
@Singleton
class BiometricPromptManager @Inject constructor() {

    companion object {
        private const val TAG = "BiometricPromptManager"
    }

    sealed class BiometricResult {
        data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) : BiometricResult()
        data class Error(val errorCode: Int, val errorMessage: String) : BiometricResult()
        data object Cancelled : BiometricResult()
    }

    /**
     * Pre-check whether the device can perform Class 3 biometric authentication.
     */
    fun canAuthenticate(activity: FragmentActivity): Int {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }

    fun isBiometricAvailable(activity: FragmentActivity): Boolean {
        return canAuthenticate(activity) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Show biometric prompt with a CryptoObject (for signing operations).
     * Returns the authenticated CryptoObject for use with Signature.sign().
     *
     * @throws BiometricException on authentication failure
     * @throws SecurityException if biometric is not available
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        cryptoObject: BiometricPrompt.CryptoObject,
        title: String = "Authenticate",
        subtitle: String = "Verify your identity to continue",
        negativeButtonText: String = "Cancel"
    ): BiometricPrompt.CryptoObject = suspendCancellableCoroutine { continuation ->

        val canAuth = canAuthenticate(activity)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            val msg = when (canAuth) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware available"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware temporarily unavailable"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometrics enrolled. Please set up fingerprint or face authentication."
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Security update required"
                else -> "Biometric authentication unavailable (code: $canAuth)"
            }
            continuation.resumeWithException(BiometricException(canAuth, msg))
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(TAG, "Biometric authentication succeeded")
                if (continuation.isActive) {
                    continuation.resume(result.cryptoObject ?: cryptoObject)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "Biometric error: $errorCode - $errString")
                if (continuation.isActive) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_LOCKOUT -> {
                            continuation.resumeWithException(
                                BiometricException(errorCode, "Too many attempts. Try again in 30 seconds.")
                            )
                        }
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            continuation.resumeWithException(
                                BiometricException(errorCode, "Biometric permanently locked. Use device PIN/password to unlock.")
                            )
                        }
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> {
                            continuation.resumeWithException(
                                BiometricException(errorCode, "Authentication cancelled")
                            )
                        }
                        else -> {
                            continuation.resumeWithException(
                                BiometricException(errorCode, errString.toString())
                            )
                        }
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric authentication failed (bad match), user can retry")
                // Don't cancel — the system allows retries automatically
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo, cryptoObject)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }

    /**
     * Simple authentication without CryptoObject (for non-signing identity checks).
     */
    suspend fun authenticateSimple(
        activity: FragmentActivity,
        title: String = "Verify Identity",
        subtitle: String = "Confirm with your biometric"
    ): Boolean = suspendCancellableCoroutine { continuation ->

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) continuation.resume(false)
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }
}

class BiometricException(val errorCode: Int, message: String) : Exception(message)
