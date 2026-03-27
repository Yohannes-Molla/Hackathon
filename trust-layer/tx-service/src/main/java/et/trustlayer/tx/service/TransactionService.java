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
import et.trustlayer.common.entity.Transaction;
import et.trustlayer.tx.dto.SignedTransactionRequest;
import et.trustlayer.tx.dto.InitiateTransactionRequest;
import et.trustlayer.tx.dto.TransactionRecordResponse;
import et.trustlayer.common.entity.UserIdentity;
import et.trustlayer.tx.repository.BiometricCredentialRepository;
import et.trustlayer.tx.repository.TransactionRepository;
import et.trustlayer.tx.repository.UserIdentityRepository;
import et.trustlayer.tx.repository.VirtualCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.List;
import java.time.Duration;
import java.time.ZoneOffset;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final BiometricCredentialRepository credentialRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final VirtualCardRepository virtualCardRepository;
    private final TransactionRepository transactionRepository;
    private final FraudScoringService fraudScoringService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private static final String NONCE_PREFIX = "tl:tx:nonce:";

    private UserIdentity resolveUserIdentity(String userIdOrKeycloakSub) {
        Optional<UserIdentity> bySub = userIdentityRepository.findByKeycloakSub(userIdOrKeycloakSub);
        if (bySub.isPresent()) {
            return bySub.get();
        }
        try {
            return userIdentityRepository.findById(UUID.fromString(userIdOrKeycloakSub))
                    .orElseThrow(() -> new RuntimeException("User not found"));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("User not found");
        }
    }

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
            VirtualCard card = cards.get(0);

            long amountMinor = parseAmountMinor(txData.get("amountMinor"));
            String merchantId = (String) txData.getOrDefault("merchantId", txData.getOrDefault("merchant", "UNKNOWN"));
            String currency = (String) txData.getOrDefault("currency", card.getCurrency());

            FraudScoringService.FraudScoreResult fraud = fraudScoringService.score(
                amountMinor,
                card.getSingleTxLimitMinor(),
                transactionRepository.findByUserIdentityIdOrderByCreatedAtDesc(credential.getUserIdentity().getId())
            );

            String status = "APPROVED";
            if ("BLOCK".equals(fraud.decision())) {
                status = "REJECTED";
            } else if ("REVIEW".equals(fraud.decision())) {
                status = "PENDING_REVIEW";
            }

            String txId = txData.get("txId") != null ? String.valueOf(txData.get("txId")) : UUID.randomUUID().toString();

            Transaction saved = transactionRepository.save(
                Transaction.builder()
                    .tenant(credential.getUserIdentity().getTenant())
                    .userIdentity(credential.getUserIdentity())
                    .virtualCard(card)
                    .merchantId(merchantId)
                    .amountMinor(amountMinor)
                    .currency(currency)
                    .status(status)
                    .nonce(nonceId)
                    .signatureVerified(true)
                    .riskScore(fraud.score())
                    .fraudScore(fraud.score())
                    .fraudFlags(String.join(",", fraud.flags()))
                    .build()
            );
            
            auditService.log(credential.getUserIdentity().getTenant(),
                    credential.getUserIdentity().getId().toString(), "TX_SUBMITTED",
                    "Transaction", txId,
                    "{\"merchantId\":\"" + merchantId + "\",\"amountMinor\":" + amountMinor
                            + ",\"currency\":\"" + currency + "\",\"status\":\"" + status
                            + "\",\"fraudScore\":" + fraud.score() + "}");

            Map<String, Object> result = new HashMap<>();
            result.put("status", status);
            result.put("txId", txId);
            result.put("fraudScore", fraud.score());
            result.put("fraudFlags", fraud.flags());
            
            // Publish to Web UI for dashboard syncing
            redisTemplate.convertAndSend(
                "APPROVED".equals(status) ? "tl:events:tx_approved" : "tl:events:tx_rejected",
                credential.getUserIdentity().getId().toString()
            );
            
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
            txState.put("userId", request.getUserId());

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

    /**
     * Web demo path: approve a merchant challenge using OIDC authentication only (no biometric signature).
     */
    @Transactional
    public Map<String, Object> approveWebTransaction(String keycloakSub, String nonce) {
        String raw = redisTemplate.opsForValue().get(NONCE_PREFIX + nonce);
        if (raw == null) {
            throw new RuntimeException("Invalid or expired nonce");
        }
        try {
            Map<String, Object> txState = objectMapper.readValue(raw, Map.class);
            String payer = (String) txState.get("userId");
            if (payer == null || !payer.equals(keycloakSub)) {
                throw new RuntimeException("Not authorized for this payment challenge");
            }

            UserIdentity user = resolveUserIdentity(keycloakSub);
            List<VirtualCard> cards = virtualCardRepository.findByUserIdentityId(user.getId());
            if (cards.isEmpty() || !"ACTIVE".equals(cards.get(0).getStatus())) {
                throw new RuntimeException("No active virtual card found for user");
            }
            VirtualCard card = cards.get(0);

            long amountMinor = parseAmountMinor(txState.get("amountMinor"));
            String merchantId = String.valueOf(txState.getOrDefault("merchantId", "UNKNOWN"));
            String currency = String.valueOf(txState.getOrDefault("currency", card.getCurrency()));

            redisTemplate.delete(NONCE_PREFIX + nonce);

            FraudScoringService.FraudScoreResult fraud = fraudScoringService.score(
                    amountMinor,
                    card.getSingleTxLimitMinor(),
                    transactionRepository.findByUserIdentityIdOrderByCreatedAtDesc(user.getId())
            );

            String status = "APPROVED";
            if ("BLOCK".equals(fraud.decision())) {
                status = "REJECTED";
            } else if ("REVIEW".equals(fraud.decision())) {
                status = "PENDING_REVIEW";
            }

            Transaction saved = transactionRepository.save(
                    Transaction.builder()
                            .tenant(user.getTenant())
                            .userIdentity(user)
                            .virtualCard(card)
                            .merchantId(merchantId)
                            .amountMinor(amountMinor)
                            .currency(currency)
                            .status(status)
                            .nonce(nonce)
                            .signatureVerified(false)
                            .riskScore(fraud.score())
                            .fraudScore(fraud.score())
                            .fraudFlags(String.join(",", fraud.flags()))
                            .build()
            );

            auditService.log(user.getTenant(),
                    user.getId().toString(), "TX_WEB_APPROVED",
                    "Transaction", saved.getId().toString(),
                    "{\"merchantId\":\"" + merchantId + "\",\"amountMinor\":" + amountMinor
                            + ",\"currency\":\"" + currency + "\",\"status\":\"" + status + "\"}");

            Map<String, Object> result = new HashMap<>();
            result.put("status", status);
            result.put("txId", saved.getId().toString());
            result.put("fraudScore", fraud.score());
            result.put("fraudFlags", fraud.flags());

            redisTemplate.convertAndSend(
                    "APPROVED".equals(status) ? "tl:events:tx_approved" : "tl:events:tx_rejected",
                    user.getId().toString()
            );

            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Web approval failed: " + e.getMessage());
        }
    }

    /**
     * Lists Redis-backed payment challenges for the payer (Keycloak subject). Demo-scale only (uses KEYS).
     */
    public List<Map<String, Object>> listPendingChallenges(String keycloakSub) {
        Set<String> keys = redisTemplate.keys(NONCE_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : keys) {
            try {
                String raw = redisTemplate.opsForValue().get(key);
                if (raw == null) {
                    continue;
                }
                Map<String, Object> txState = objectMapper.readValue(raw, Map.class);
                if (!keycloakSub.equals(txState.get("userId"))) {
                    continue;
                }
                String nonce = key.substring(NONCE_PREFIX.length());
                Map<String, Object> row = new HashMap<>();
                row.put("nonce", nonce);
                row.put("amountMinor", txState.get("amountMinor"));
                row.put("merchantId", txState.get("merchantId"));
                row.put("currency", txState.get("currency"));
                row.put("txId", txState.get("txId"));
                out.add(row);
            } catch (Exception ignored) {
                // skip malformed entries
            }
        }
        return out;
    }

    public List<TransactionRecordResponse> getTransactionHistory(String userIdOrKeycloakSub) {
        UserIdentity user = resolveUserIdentity(userIdOrKeycloakSub);
        return transactionRepository.findByUserIdentityIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(tx -> TransactionRecordResponse.builder()
                .txId(tx.getId().toString())
                .status(tx.getStatus())
                .merchantId(tx.getMerchantId())
                .amountMinor(tx.getAmountMinor())
                .currency(tx.getCurrency())
                .timestamp(tx.getCreatedAt().toInstant(ZoneOffset.UTC).toString())
                .build())
            .toList();
    }

    public List<TransactionRecordResponse> getMerchantTransactionHistory(String merchantId) {
        return transactionRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
            .stream()
            .map(tx -> TransactionRecordResponse.builder()
                .txId(tx.getId().toString())
                .status(tx.getStatus())
                .merchantId(tx.getMerchantId())
                .amountMinor(tx.getAmountMinor())
                .currency(tx.getCurrency())
                .timestamp(tx.getCreatedAt().toInstant(ZoneOffset.UTC).toString())
                .build())
            .toList();
    }

    private long parseAmountMinor(Object amountObj) {
        if (amountObj == null) {
            return 0L;
        }
        if (amountObj instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(amountObj));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
