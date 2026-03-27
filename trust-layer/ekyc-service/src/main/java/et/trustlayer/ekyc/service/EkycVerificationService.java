package et.trustlayer.ekyc.service;

import et.trustlayer.common.entity.UserIdentity;
import et.trustlayer.ekyc.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EkycVerificationService {

    private final UserIdentityRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AuditService auditService;
    private static final String SESSION_PREFIX = "tl:session:";
    private static final String EKYC_PUB_CHAN = "tl:events:ekyc_complete";

    @Value("${trustlayer.ai.base-url:http://localhost:5000}")
    private String aiBaseUrl;

    @Transactional
    public Map<String, Object> verifyDocument(String sessionId, MultipartFile documentImage, MultipartFile livenessFrames) {
        log.info("Starting eKYC verification for session {}", sessionId);

        // 1. Fetch SessionState from Redis to map sessionId -> userId
        String sessionJson = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (sessionJson == null) {
            throw new RuntimeException("Session not found or expired");
        }

        UUID userId;
        try {
            Map<String, Object> state = objectMapper.readValue(sessionJson, Map.class);
            String userIdStr = (String) state.get("userId");
            if (userIdStr == null) {
                throw new RuntimeException("Session state invalid: missing userId");
            }
            userId = UUID.fromString(userIdStr);
            
            // Keep existing state here; final state is set after verification result is known.
            redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, objectMapper.writeValueAsString(state));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read session state", e);
        }

        // 2. Load UserIdentity from DB
        UserIdentity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3. Integrate with AI service (OCR + liveness + anti-spoof + risk score)
        Map<String, Object> risk = null;
        Map<String, Object> docFields = new HashMap<>();
        String verificationMethod = "AI";
        try {
            risk = invokeRiskEndpoint(documentImage, livenessFrames);
            docFields = extractDocFields(risk);
        } catch (Exception ex) {
            // Degraded fallback keeps flow operable during AI service outages.
            log.warn("AI verification unavailable, falling back to manual-review mode: {}", ex.getMessage());
            verificationMethod = "MANUAL_REVIEW";
        }

        Map<String, Object> verifiedClaims = new HashMap<>();
        verifiedClaims.put("given_name", docFields.getOrDefault("name", user.getGivenName()));
        verifiedClaims.put("family_name", user.getFamilyName());
        verifiedClaims.put("date_of_birth", user.getDateOfBirth().toString());
        verifiedClaims.put("nationality", docFields.getOrDefault("nationality", user.getNationality()));
        verifiedClaims.put("document_number", docFields.getOrDefault("document_number", "ET" + System.currentTimeMillis()));

        boolean aiVerified = risk != null;

        // 4. Update Application User status
        user.setOnboardingState(aiVerified ? "EKYC_COMPLETE" : "EKYC_PENDING_REVIEW");
        user.setEkycVerificationId("ver_" + UUID.randomUUID().toString().substring(0, 8));
        user.setRiskScore(extractRiskScore(risk));
        userRepository.save(user);

        // 5. Notify auth-server via Redis Pub/Sub only when verification is actually complete.
        if (aiVerified) {
            log.info("Publishing EKYC_COMPLETE to auth-server for session {}", sessionId);
            redisTemplate.convertAndSend(EKYC_PUB_CHAN, sessionId);
        }

        try {
            Map<String, Object> state = objectMapper.readValue(
                    redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId), Map.class);
            state.put("state", aiVerified ? "EKYC_COMPLETE" : "EKYC_PENDING_REVIEW");
            redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, objectMapper.writeValueAsString(state));
        } catch (Exception ex) {
            log.warn("Failed to update session state for {}: {}", sessionId, ex.getMessage());
        }

        String status = aiVerified ? "VERIFIED" : "PENDING_REVIEW";

        auditService.log(user.getTenant(), userId.toString(),
                aiVerified ? "EKYC_VERIFIED" : "EKYC_PENDING_REVIEW",
                "UserIdentity", userId.toString(),
                "{\"sessionId\":\"" + sessionId + "\",\"status\":\"" + status
                        + "\",\"verificationMethod\":\"" + verificationMethod
                        + "\",\"riskScore\":" + extractRiskScore(risk) + "}");

        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("verifiedClaims", verifiedClaims);
        result.put("risk", risk);
        result.put("verification_method", verificationMethod);

        return result;
    }

    public Map<String, Object> getVerificationStatus(String sessionId) {
        String sessionJson = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (sessionJson == null) {
            throw new RuntimeException("Session not found or expired");
        }
        
        try {
            Map<String, Object> state = objectMapper.readValue(sessionJson, Map.class);
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("state", state.get("state"));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read session state", e);
        }
    }

    public Map<String, Object> getVerifiedClaims(String userId) {
        UserIdentity user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        if (!"EKYC_COMPLETE".equals(user.getOnboardingState()) && !"LIVE".equals(user.getOnboardingState())) {
            throw new RuntimeException("User has not completed eKYC");
        }

        Map<String, Object> verifiedClaims = new HashMap<>();
        verifiedClaims.put("given_name", user.getGivenName());
        verifiedClaims.put("family_name", user.getFamilyName());
        verifiedClaims.put("date_of_birth", user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null);
        verifiedClaims.put("nationality", user.getNationality());
        
        return verifiedClaims;
    }

    private Map<String, Object> invokeRiskEndpoint(MultipartFile documentImage, MultipartFile livenessFrames) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("document", asResource("documentImage", documentImage));
        if (livenessFrames != null && !livenessFrames.isEmpty()) {
            body.add("liveness_frames", asResource("livenessFrames", livenessFrames));
        } else {
            body.add("liveness_frames", asResource("livenessFrames", documentImage));
        }

        ResponseEntity<Map> response = restTemplate.postForEntity(
            aiBaseUrl + "/api/ai/risk-score",
            new HttpEntity<>(body, headers),
            Map.class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RestClientException("Unexpected AI response");
        }
        return response.getBody();
    }

    private ByteArrayResource asResource(String filename, MultipartFile file) throws IOException {
        return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename + ".bin";
            }
        };
    }

    private Map<String, Object> extractDocFields(Map<String, Object> risk) {
        if (risk == null) {
            return Map.of();
        }
        Object raw = risk.get("document_fields");
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    private Integer extractRiskScore(Map<String, Object> risk) {
        if (risk == null) {
            return 50;
        }
        Object riskObj = risk.get("risk");
        if (riskObj instanceof Map<?, ?> riskMap) {
            Object rawScore = riskMap.get("risk_score");
            if (rawScore instanceof Number number) {
                return number.intValue();
            }
        }
        return 50;
    }
}
