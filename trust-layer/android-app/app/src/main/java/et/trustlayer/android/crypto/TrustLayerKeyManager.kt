package et.trustlayer.android.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2B — TrustLayerKeyManager
 *
 * Manages hardware-backed EC P-256 signing keys in Android KeyStore (StrongBox/TEE).
 * Keys are:
 *   - Gated by BIOMETRIC_STRONG (Class 3)
 *   - Invalidated on new biometric enrollment
 *   - Attested via setAttestationChallenge for backend verification
 */
@Singleton
class TrustLayerKeyManager @Inject constructor() {

    companion object {
        private const val TAG = "TrustLayerKeyManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "trust_layer_sign_"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Generate a signing key pair in StrongBox (falls back to TEE if StrongBox unavailable).
     * @param userId The user's UUID, used in the key alias.
     * @param serverChallenge A fresh nonce from the backend for attestation.
     */
    fun generateSigningKey(userId: String, serverChallenge: ByteArray) {
        val alias = "$KEY_ALIAS_PREFIX$userId"

        // Delete existing key if present
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }

        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256)
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            setInvalidatedByBiometricEnrollment(true)
            setAttestationChallenge(serverChallenge)
            setIsStrongBoxBacked(true) // Try StrongBox first
        }

        try {
            generateKeyPairInternal(specBuilder.build())
            Log.i(TAG, "Key generated in StrongBox for user $userId")
        } catch (e: StrongBoxUnavailableException) {
            Log.w(TAG, "StrongBox unavailable, falling back to TEE", e)
            val fallbackSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).apply {
                setDigests(KeyProperties.DIGEST_SHA256)
                setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
                setInvalidatedByBiometricEnrollment(true)
                setAttestationChallenge(serverChallenge)
                // No setIsStrongBoxBacked — falls back to TEE
            }
            generateKeyPairInternal(fallbackSpec.build())
            Log.i(TAG, "Key generated in TEE for user $userId")
        }
    }

    private fun generateKeyPairInternal(spec: KeyGenParameterSpec) {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        kpg.initialize(spec)
        kpg.generateKeyPair()
    }

    /**
     * Export the public key as a JWK JSON string.
     */
    fun exportPublicKeyAsJwk(userId: String): String {
        val alias = "$KEY_ALIAS_PREFIX$userId"
        val entry = keyStore.getCertificate(alias)
            ?: throw IllegalStateException("No key found for alias $alias")

        val ecPublicKey = entry.publicKey as ECPublicKey

        val jwk = ECKey.Builder(Curve.P_256, ecPublicKey)
            .keyID(alias)
            .build()

        return jwk.toJSONString()
    }

    /**
     * Compute the JWK SHA-256 thumbprint for DPoP binding.
     */
    fun computeJwkThumbprint(userId: String): String {
        val alias = "$KEY_ALIAS_PREFIX$userId"
        val entry = keyStore.getCertificate(alias)
            ?: throw IllegalStateException("No key found for alias $alias")

        val ecPublicKey = entry.publicKey as ECPublicKey
        val jwk = ECKey.Builder(Curve.P_256, ecPublicKey)
            .keyID(alias)
            .build()

        return jwk.computeThumbprint().toString()
    }

    /**
     * Extract the attestation certificate chain for backend validation.
     * Returns a Base64-encoded chain (PEM-like).
     */
    fun getAttestationCertChain(userId: String): String {
        val alias = "$KEY_ALIAS_PREFIX$userId"
        val certChain = keyStore.getCertificateChain(alias)
            ?: throw IllegalStateException("No attestation chain for alias $alias")

        return certChain.joinToString("\n") { cert ->
            val x509 = cert as X509Certificate
            "-----BEGIN CERTIFICATE-----\n" +
                    Base64.encodeToString(x509.encoded, Base64.NO_WRAP) +
                    "\n-----END CERTIFICATE-----"
        }
    }

    /**
     * Get the Signature object for signing (will require biometric auth via CryptoObject).
     */
    fun getSignatureForSigning(userId: String): java.security.Signature {
        val alias = "$KEY_ALIAS_PREFIX$userId"
        val privateKey = (keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry).privateKey

        return java.security.Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
        }
    }

    /**
     * Check if a signing key exists for the user.
     */
    fun hasKey(userId: String): Boolean {
        return keyStore.containsAlias("$KEY_ALIAS_PREFIX$userId")
    }

    /**
     * Delete the signing key (e.g., on logout or credential revocation).
     */
    fun deleteKey(userId: String) {
        val alias = "$KEY_ALIAS_PREFIX$userId"
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
