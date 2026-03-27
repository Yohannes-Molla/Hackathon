package et.trustlayer.authserver.dpop;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import et.trustlayer.authserver.session.NonceCacheService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
@Slf4j
public class DPoPProofValidator {

    private final NonceCacheService nonceCacheService;

    public DPoPValidationResult validate(String dpopHeader, String htm, String htu, String accessToken) {
        if (dpopHeader == null || dpopHeader.isBlank()) {
            return DPoPValidationResult.invalid("Missing DPoP header");
        }

        try {
            JWSObject jwsObject = JWSObject.parse(dpopHeader);
            Map<String, Object> payload = jwsObject.getPayload().toJSONObject();

            // 1. Validate typ Header
            if (!"dpop+jwt".equalsIgnoreCase(jwsObject.getHeader().getType().toString())) {
                return DPoPValidationResult.invalid("Invalid 'typ' header");
            }

            // 2. Validate Jwk
            JWK jwk = jwsObject.getHeader().getJWK();
            if (jwk == null || !jwk.isPrivate() == false) { // JWK must be public
                return DPoPValidationResult.invalid("Missing or invalid public JWK");
            }

            // 3. Signature Validation
            JWSVerifier verifier = createVerifier(jwk);
            if (verifier == null) {
                return DPoPValidationResult.invalid("Unsupported JWK key type for DPoP proof");
            }
            if (!jwsObject.verify(verifier)) {
                return DPoPValidationResult.invalid("DPoP proof signature verification failed");
            }

            // 4. Validate htm (HTTP Method)
            String proofHtm = (String) payload.get("htm");
            if (!htm.equalsIgnoreCase(proofHtm)) {
                return DPoPValidationResult.invalid("Invalid 'htm'");
            }

            // 5. Validate htu (HTTP URL)
            String proofHtu = (String) payload.get("htu");
            if (!htu.equalsIgnoreCase(proofHtu)) {
                return DPoPValidationResult.invalid("Invalid 'htu'");
            }

            // 6. Validate ath (Access Token Hash) if accessToken is provided
            if (accessToken != null) {
                String ath = (String) payload.get("ath");
                if (ath == null || !ath.equals(computeSha256Str(accessToken))) {
                    return DPoPValidationResult.invalid("Invalid 'ath'");
                }
            }

            // 7. Validate/consume nonce
            String nonce = (String) payload.get("nonce");
            if (nonce == null || !nonceCacheService.consumeNonce(nonce)) {
                return DPoPValidationResult.invalid("Invalid or replayed 'nonce'");
            }

            return DPoPValidationResult.valid(jwk.computeThumbprint().toString());

        } catch (Exception e) {
            return DPoPValidationResult.invalid("Malformed DPoP proof: " + e.getMessage());
        }
    }

    private JWSVerifier createVerifier(JWK jwk) throws Exception {
        if (jwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey);
        } else if (jwk instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey);
        }
        return null;
    }

    private String computeSha256Str(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes());
        return Base64URL.encode(hash).toString();
    }
}
