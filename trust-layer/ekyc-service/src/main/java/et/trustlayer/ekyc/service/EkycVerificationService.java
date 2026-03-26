package et.trustlayer.ekyc.service;

import et.trustlayer.common.entity.UserIdentity;
import et.trustlayer.ekyc.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private static final String SESSION_PREFIX = "tl:session:";
    private static final String EKYC_PUB_CHAN = "tl:events:ekyc_complete";

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
            
            // Update state safely
            state.put("state", "EKYC_COMPLETE");
            redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, objectMapper.writeValueAsString(state));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read session state", e);
        }

        // 2. Load UserIdentity from DB
        UserIdentity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3. (Mock) integration with ML model/AI to extract claims and verify liveness
        // In reality, this would submit bytes to an AI model server and await response.
        log.info("Mock AI Verification on doc size: {} and liveness frames: {}",
                documentImage.getSize(), 
                livenessFrames != null ? livenessFrames.getSize() : 0);

        // Simulated processing time
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        // Mock resulting claims aligned with what might be parsed from ID
        Map<String, Object> verifiedClaims = new HashMap<>();
        verifiedClaims.put("given_name", user.getGivenName());
        verifiedClaims.put("family_name", user.getFamilyName());
        verifiedClaims.put("date_of_birth", user.getDateOfBirth().toString());
        verifiedClaims.put("nationality", user.getNationality());
        verifiedClaims.put("document_number", "ET" + System.currentTimeMillis());

        // 4. Update Application User status
        user.setOnboardingState("EKYC_COMPLETE");
        user.setEkycVerificationId("ver_" + UUID.randomUUID().toString().substring(0, 8));
        userRepository.save(user);

        // 5. Notify auth-server via Redis Pub/Sub so websocket pushes to user web dashboard
        log.info("Publishing EKYC_COMPLETE to auth-server for session {}", sessionId);
        redisTemplate.convertAndSend(EKYC_PUB_CHAN, sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "VERIFIED");
        result.put("verifiedClaims", verifiedClaims);

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
}
