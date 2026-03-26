package et.trustlayer.android.di

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface TrustLayerApi {

    // Health check
    @GET("actuator/health")
    suspend fun health(): Response<ResponseBody>

    // Credential Registration (Phase 2E)
    @POST("api/credentials/register")
    suspend fun registerCredential(
        @Header("X-Tenant-ID") tenantId: String,
        @Header("X-User-ID") userId: String,
        @Header("DPoP") dpopProof: String,
        @Body body: CredentialRegistrationBody
    ): Response<CredentialRegistrationResponse>

    // eKYC Document Upload (Phase 2D)
    @POST("api/ekyc/verify")
    @Multipart
    suspend fun uploadEkycDocument(
        @Header("X-Tenant-ID") tenantId: String,
        @Header("Authorization") bearerToken: String,
        @Part("sessionId") sessionId: String,
        @Part("documentImage") documentImage: RequestBody,
        @Part("livenessFrames") livenessFrames: RequestBody? = null
    ): Response<EkycVerificationResponse>

    // Transaction Submit (Phase 2F)
    @POST("api/tx/submit")
    suspend fun submitTransaction(
        @Header("X-Tenant-ID") tenantId: String,
        @Header("Authorization") bearerToken: String,
        @Header("DPoP") dpopProof: String,
        @Body body: SignedTransactionBody
    ): Response<TransactionResponse>

    // PAR - Pushed Authorization Request (Phase 2E)
    @FormUrlEncoded
    @POST("oauth2/par")
    suspend fun pushedAuthorizationRequest(
        @Field("client_id") clientId: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("response_type") responseType: String = "code",
        @Field("scope") scope: String,
        @Field("code_challenge") codeChallenge: String,
        @Field("code_challenge_method") codeChallengeMethod: String = "S256",
        @Header("DPoP") dpopProof: String
    ): Response<ParResponse>

    // Token Exchange (Phase 2E)
    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun exchangeToken(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String,
        @Header("DPoP") dpopProof: String
    ): Response<TokenResponse>
}

// --- Data classes ---

data class CredentialRegistrationBody(
    val jwk: String,
    val attestationCertChain: String,
    val deviceId: String
)

data class CredentialRegistrationResponse(
    val keyId: String
)

data class EkycVerificationResponse(
    val status: String,      // VERIFIED, REJECTED, PENDING
    val verifiedClaims: Map<String, Any>? = null
)

data class SignedTransactionBody(
    val payloadBase64: String,
    val signatureBase64: String,
    val keyId: String,
    val algorithm: String = "ES256"
)

data class TransactionResponse(
    val txId: String,
    val status: String // APPROVED, REJECTED, PENDING
)

data class ParResponse(
    val request_uri: String,
    val expires_in: Int
)

data class TokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val token_type: String,
    val expires_in: Int,
    val scope: String
)
