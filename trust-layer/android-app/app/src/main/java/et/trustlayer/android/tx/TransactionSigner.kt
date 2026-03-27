package et.trustlayer.android.tx

import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.google.gson.GsonBuilder
import et.trustlayer.android.auth.DPoPProofGenerator
import et.trustlayer.android.auth.OidcFlowManager
import et.trustlayer.android.biometric.BiometricPromptManager
import et.trustlayer.android.crypto.TrustLayerKeyManager
import et.trustlayer.android.di.SignedTransactionBody
import et.trustlayer.android.di.TrustLayerApi
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2F — Transaction Signer
 *
 * Signs transaction payloads with ECDSA-SHA256 using the biometric-gated
 * KeyStore key. Produces a SignedCryptogram for backend validation.
 *
 * Flow:
 *   1. Build canonical JSON (sorted keys, no whitespace)
 *   2. Get Signature from KeyStore (triggers UserNotAuthenticatedException)
 *   3. BiometricPromptManager authenticates with CryptoObject(Signature)
 *   4. Sign the canonical payload
 *   5. POST to /api/tx/submit with DPoP proof
 */
@Singleton
class TransactionSigner @Inject constructor(
    private val keyManager: TrustLayerKeyManager,
    private val biometricManager: BiometricPromptManager,
    private val dpopGenerator: DPoPProofGenerator,
    private val oidcManager: OidcFlowManager,
    private val api: TrustLayerApi
) {

    companion object {
        private const val TAG = "TransactionSigner"
    }

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .create()

    data class TransactionPayload(
        val txId: String,
        val amount: Double,
        val currency: String,
        val merchant: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class SignResult(
        val success: Boolean,
        val txId: String? = null,
        val status: String? = null,
        val error: String? = null
    )

    /**
     * Sign and submit a transaction.
     * This triggers the biometric prompt for hardware-backed signing.
     */
    suspend fun signAndSubmit(
        activity: FragmentActivity,
        userId: String,
        tenantId: String,
        payload: TransactionPayload
    ): SignResult {
        return try {
            // 1. Build canonical JSON (sorted keys, compact)
            val canonicalJson = buildCanonicalJson(payload)
            val canonicalBytes = canonicalJson.toByteArray(Charsets.UTF_8)

            // 2. Get Signature object from KeyStore
            val signature = keyManager.getSignatureForSigning(userId)

            // 3. Authenticate with biometric (wraps the Signature in CryptoObject)
            val cryptoObject = BiometricPrompt.CryptoObject(signature)
            val authedCrypto = biometricManager.authenticate(
                activity = activity,
                cryptoObject = cryptoObject,
                title = "Approve Transaction",
                subtitle = "ETB ${payload.amount} to ${payload.merchant}"
            )

            // 4. Sign the payload
            val authedSignature = authedCrypto.signature
                ?: throw IllegalStateException("CryptoObject missing Signature after auth")
            authedSignature.update(canonicalBytes)
            val sig = authedSignature.sign()

            val payloadB64 = Base64.encodeToString(canonicalBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val sigB64 = Base64.encodeToString(sig, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            Log.i(TAG, "Transaction signed for ${payload.txId}")

            // 5. Submit to backend
            val accessToken = oidcManager.getAccessToken()
                ?: return SignResult(false, error = "No access token")

            val dpopProof = dpopGenerator.generateProof(
                userId = userId,
                htm = "POST",
                htu = "http://10.0.2.2:8080/api/tx/submit",
                ath = dpopGenerator.computeAccessTokenHash(accessToken)
            )

            val response = api.submitTransaction(
                tenantId = tenantId,
                bearerToken = "DPoP $accessToken",
                dpopProof = dpopProof,
                body = SignedTransactionBody(
                    payloadBase64 = payloadB64,
                    signatureBase64 = sigB64,
                    keyId = "trust_layer_sign_$userId",
                    algorithm = "ES256"
                )
            )

            if (response.isSuccessful) {
                val body = response.body()
                SignResult(true, txId = body?.txId, status = body?.status)
            } else {
                SignResult(false, error = "Server rejected: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transaction signing failed", e)
            SignResult(false, error = e.message)
        }
    }

    /**
     * Build canonical JSON: sorted keys, no whitespace, no HTML escaping.
     */
    private fun buildCanonicalJson(payload: TransactionPayload): String {
        val sorted = TreeMap<String, Any>()
        sorted["txId"] = payload.txId
        sorted["amount"] = payload.amount
        sorted["currency"] = payload.currency
        sorted["merchant"] = payload.merchant
        sorted["timestamp"] = payload.timestamp
        return gson.toJson(sorted)
    }
}
