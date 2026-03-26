package et.trustlayer.android.auth

import android.util.Base64
import android.util.Log
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import et.trustlayer.android.crypto.TrustLayerKeyManager
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2E — DPoP Proof Generator
 *
 * Generates DPoP proof JWTs per RFC 9449, signed by the hardware-backed
 * KeyStore EC key. The proof binds the access token to the specific
 * client key (cnf.jkt).
 *
 * NOTE: Because the KeyStore private key requires biometric auth,
 * the actual signing happens through a CryptoObject-wrapped Signature.
 * This class builds the JWT structure; the caller must use
 * BiometricPromptManager to authenticate before calling generateProof().
 */
@Singleton
class DPoPProofGenerator @Inject constructor(
    private val keyManager: TrustLayerKeyManager
) {

    companion object {
        private const val TAG = "DPoPProofGenerator"
    }

    /**
     * Build a DPoP proof JWT.
     *
     * @param userId   The user's ID (to look up the KeyStore alias)
     * @param htm      HTTP method (GET, POST, etc.)
     * @param htu      HTTP target URI
     * @param ath      Access token hash (SHA-256, base64url of access_token), optional
     * @param nonce    Server-provided DPoP nonce, optional
     * @return Serialized JWS compact string
     */
    fun generateProof(
        userId: String,
        htm: String,
        htu: String,
        ath: String? = null,
        nonce: String? = null
    ): String {
        val jwkJson = keyManager.exportPublicKeyAsJwk(userId)
        val ecKey = ECKey.parse(jwkJson)

        // Build JWK header with the public key
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(ecKey.toPublicJWK())
            .build()

        // Build claims
        val claimsBuilder = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .claim("htm", htm)
            .claim("htu", htu)
            .issueTime(Date())

        if (ath != null) {
            claimsBuilder.claim("ath", ath)
        }
        if (nonce != null) {
            claimsBuilder.claim("nonce", nonce)
        }

        val claims = claimsBuilder.build()
        val signedJwt = SignedJWT(header, claims)

        // Sign with the KeyStore-backed EC key
        // NOTE: This will throw UserNotAuthenticatedException if biometric
        // has not been performed. The caller should wrap this in BiometricPromptManager.
        val signature = keyManager.getSignatureForSigning(userId)
        val signingInput = signedJwt.signingInput
        signature.update(signingInput)
        val sig = signature.sign()

        // Manually set the signature on the JWT
        val base64Sig = Base64.encodeToString(sig, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val parts = signedJwt.serialize().split(".")
        return "${parts[0]}.${parts[1]}.$base64Sig"
    }

    /**
     * Compute the access token hash (ath) for DPoP binding.
     */
    fun computeAccessTokenHash(accessToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
