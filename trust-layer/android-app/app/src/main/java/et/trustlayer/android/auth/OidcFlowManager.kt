package et.trustlayer.android.auth

import android.content.SharedPreferences
import android.util.Log
import et.trustlayer.android.di.CredentialRegistrationBody
import et.trustlayer.android.di.TrustLayerApi
import et.trustlayer.android.crypto.TrustLayerKeyManager
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Phase 2E — OIDC PAR Flow Manager
 *
 * Orchestrates the full FAPI 2.0 flow:
 *   1. PAR (Pushed Authorization Request) with PKCE
 *   2. Browser-based authorization
 *   3. Token exchange with DPoP proof
 *   4. Credential registration with the backend
 *   5. Secure token storage in EncryptedSharedPreferences
 */
@Singleton
class OidcFlowManager @Inject constructor(
    private val api: TrustLayerApi,
    private val keyManager: TrustLayerKeyManager,
    private val dpopGenerator: DPoPProofGenerator,
    @Named("encrypted") private val securePrefs: SharedPreferences
) {

    companion object {
        private const val TAG = "OidcFlowManager"
        private const val CLIENT_ID = "trust-layer-android"
        private const val REDIRECT_URI = "trustlayer://callback"
        private const val SCOPE = "openid profile ekyc:verify tx:sign"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }

    data class PkceChallenge(
        val codeVerifier: String,
        val codeChallenge: String
    )

    /**
     * Generate PKCE S256 challenge pair.
     */
    fun generatePkceChallenge(): PkceChallenge {
        val verifier = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        val challenge = android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        return PkceChallenge(verifier, challenge)
    }

    /**
     * Step 1: Push Authorization Request (PAR).
     * Returns the request_uri for the browser redirect.
     */
    suspend fun initiateParRequest(userId: String): String? {
        return try {
            val pkce = generatePkceChallenge()
            storePkceVerifier(pkce.codeVerifier)

            val dpopProof = dpopGenerator.generateProof(
                userId = userId,
                htm = "POST",
                htu = "http://10.0.2.2:8080/oauth2/par"
            )

            val response = api.pushedAuthorizationRequest(
                clientId = CLIENT_ID,
                redirectUri = REDIRECT_URI,
                scope = SCOPE,
                codeChallenge = pkce.codeChallenge,
                dpopProof = dpopProof
            )

            if (response.isSuccessful) {
                response.body()?.request_uri
            } else {
                Log.e(TAG, "PAR failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "PAR request error", e)
            null
        }
    }

    /**
     * Step 2: Exchange authorization code for tokens.
     */
    suspend fun exchangeCodeForTokens(userId: String, authorizationCode: String): Boolean {
        return try {
            val codeVerifier = retrievePkceVerifier() ?: return false

            val dpopProof = dpopGenerator.generateProof(
                userId = userId,
                htm = "POST",
                htu = "http://10.0.2.2:8080/oauth2/token"
            )

            val response = api.exchangeToken(
                code = authorizationCode,
                redirectUri = REDIRECT_URI,
                clientId = CLIENT_ID,
                codeVerifier = codeVerifier,
                dpopProof = dpopProof
            )

            if (response.isSuccessful) {
                val tokens = response.body() ?: return false
                storeTokens(tokens.access_token, tokens.refresh_token)
                Log.i(TAG, "Token exchange successful")
                true
            } else {
                Log.e(TAG, "Token exchange failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange error", e)
            false
        }
    }

    /**
     * Step 3: Register the biometric credential with the backend.
     */
    suspend fun registerCredential(
        userId: String,
        tenantId: String,
        deviceId: String
    ): String? {
        return try {
            val jwk = keyManager.exportPublicKeyAsJwk(userId)
            val attestationChain = keyManager.getAttestationCertChain(userId)
            val accessToken = getAccessToken() ?: return null

            val dpopProof = dpopGenerator.generateProof(
                userId = userId,
                htm = "POST",
                htu = "http://10.0.2.2:8080/api/credentials/register",
                ath = dpopGenerator.computeAccessTokenHash(accessToken)
            )

            val response = api.registerCredential(
                tenantId = tenantId,
                userId = userId,
                dpopProof = dpopProof,
                body = CredentialRegistrationBody(
                    jwk = jwk,
                    attestationCertChain = attestationChain,
                    deviceId = deviceId
                )
            )

            if (response.isSuccessful) {
                val keyId = response.body()?.keyId
                Log.i(TAG, "Credential registered: keyId=$keyId")
                keyId
            } else {
                Log.e(TAG, "Credential registration failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Credential registration error", e)
            null
        }
    }

    // --- Token Storage ---

    private fun storeTokens(accessToken: String, refreshToken: String?) {
        securePrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = securePrefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = securePrefs.getString(KEY_REFRESH_TOKEN, null)

    private fun storePkceVerifier(verifier: String) {
        securePrefs.edit().putString("pkce_verifier", verifier).apply()
    }

    private fun retrievePkceVerifier(): String? {
        val verifier = securePrefs.getString("pkce_verifier", null)
        securePrefs.edit().remove("pkce_verifier").apply() // One-time use
        return verifier
    }

    fun clearSession() {
        securePrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }
}
