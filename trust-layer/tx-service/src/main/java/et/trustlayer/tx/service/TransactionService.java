package et.trustlayer.tx.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.util.Base64URL;
import et.trustlayer.common.entity.VirtualCard;
import et.trustlayer.common.entity.BiometricCredential;
import et.trustlayer.tx.dto.SignedTransactionRequest;
import et.trustlayer.tx.dto.InitiateTransactionRequest;
import et.trustlayer.tx.dto.TransactionRecordResponse;
import et.trustlayer.tx.repository.BiometricCredentialRepository;
import et.trustlayer.tx.repository.VirtualCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final BiometricCredentialRepository credentialRepository;
    private final VirtualCardRepository virtualCardRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String NONCE_PREFIX = "tl:tx:nonce:";

    @Transactional
    public Map<String, Object> verifyAndSubmitTransaction(SignedTransactionRequest request) {
        log.info("Processing signed transaction request for keyId: {}", request.getKeyId());
        
        try {
            // 1. Fetch the bound credential to get the public key
            BiometricCredential credential = credentialRepository.findByKeyId(request.getKeyId())
                .orElseThrow(() -> new RuntimeException("Credential not found for keyId: " + request.getKeyId()));
                
            // 2. Parse the public key from the database
            JWK jwk = JWK.parse(credential.getPublicKeyJwk());
            if (!(jwk instanceof ECKey)) {
                throw new RuntimeException("Unsupported key type, expected ECKey");
            }
            ECKey ecKey = (ECKey) jwk;
            
            // 3. Reconstruct JWS to verify the signature using Nimbus JOSE JWT
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
            Payload payload = new Payload(new Base64URL(request.getPayloadBase64()));
            Base64URL signature = new Base64URL(request.getSignatureBase64());
            
            JWSObject jwsObject = new JWSObject(
                header.toBase64URL(),
                payload.toBase64URL(),
                signature
            );
            
            // 4. Verify Signature
            ECDSAVerifier verifier = new ECDSAVerifier(ecKey);
            boolean verified = jwsObject.verify(verifier);
            
            if (!verified) {
                log.error("Transaction signature verification failed for keyId: {}", request.getKeyId());
                throw new RuntimeException("Cryptographic signature verification failed");
            }
            
            // 6. Signature verified! Decode canonical JSON payload safely
            String payloadJson = payload.toString();
            Map<String, Object> txData = objectMapper.readValue(payloadJson, Map.class);
            log.info("Signature valid. Processing transaction: {}", txData);
            
            // Validate nonce to prevent replay attacks
            String nonceId = (String) txData.get("nonce");
            if (nonceId != null) {
                Boolean deleted = redisTemplate.delete(NONCE_PREFIX + nonceId);
                if (!Boolean.TRUE.equals(deleted)) {
                    throw new RuntimeException("Transaction nonce invalid or expired (Replay Detected)");
                }
            }
            
            // Verify Virtual Card limits
            List<VirtualCard> cards = virtualCardRepository.findByUserIdentityId(credential.getUserIdentity().getId());
            if (cards.isEmpty() || !"ACTIVE".equals(cards.get(0).getStatus())) {
                throw new RuntimeException("No active virtual card found for user");
            }
            
            // 7. Success response
            Map<String, Object> result = new HashMap<>();
            result.put("status", "APPROVED");
            result.put("txId", txData.get("txId") != null ? txData.get("txId") : UUID.randomUUID().toString());
            
            // Publish to Web UI for dashboard syncing
            redisTemplate.convertAndSend("tl:events:tx_approved", credential.getUserIdentity().getId().toString());
            
            return result;
            
        } catch (Exception e) {
            log.error("Transaction processing error", e);
            throw new RuntimeException("Transaction verification failed: " + e.getMessage());
        }
    }

    public Map<String, Object> initiateTransaction(InitiateTransactionRequest request) {
        UUID nonceId = UUID.randomUUID();
        
        // Save the challenge context in Redis for 60 seconds
        try {
            Map<String, Object> txState = new HashMap<>();
            txState.put("txId", UUID.randomUUID().toString());
            txState.put("amountMinor", request.getAmountMinor());
            txState.put("merchantId", request.getMerchantId());
            txState.put("currency", request.getCurrency());
            
            redisTemplate.opsForValue().set(NONCE_PREFIX + nonceId, objectMapper.writeValueAsString(txState), Duration.ofSeconds(60));
        } catch (Exception e) {
            throw new RuntimeException("Failed to cache transaction state", e);
        }
        
        // Mock FCM push
        log.info("Mock FCM Push sent to user {} to approve {} {} at {}", 
                 request.getUserId(), 
                 (request.getAmountMinor() / 100.0), 
                 request.getCurrency(), 
                 request.getMerchantId());
                 
        Map<String, Object> response = new HashMap<>();
        response.put("nonce", nonceId.toString());
        response.put("status", "CHALLENGE_ISSUED");
        return response;
    }

    public List<TransactionRecordResponse> getTransactionHistory(String userId) {
        // Return mock data for hackathon. In reality, query from an AuditLog or Transaction table.
        List<TransactionRecordResponse> history = new ArrayList<>();
        history.add(TransactionRecordResponse.builder()
                .txId(UUID.randomUUID().toString())
                .status("APPROVED")
                .merchantId("Addis Supermarket")
                .amountMinor(125000L) // 1250 ETB
                .currency("ETB")
                .timestamp(Instant.now().minus(Duration.ofDays(1)).toString())
                .build());
                
        history.add(TransactionRecordResponse.builder()
                .txId(UUID.randomUUID().toString())
                .status("APPROVED")
                .merchantId("Ethio Telecom")
                .amountMinor(2500L) // 25 ETB
                .currency("ETB")
                .timestamp(Instant.now().minus(Duration.ofDays(2)).toString())
                .build());
                
        return history;
    }
}
