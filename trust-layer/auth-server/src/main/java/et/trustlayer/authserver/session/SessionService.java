package et.trustlayer.authserver.session;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class SessionService {
    private static final String SESSION_PREFIX = "tl:session:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UUID createSession(SessionState state) {
        UUID sessionId = UUID.randomUUID();
        saveSession(sessionId, state);
        return sessionId;
    }

    public void saveSession(UUID sessionId, SessionState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, json, Duration.ofMinutes(30));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize session", e);
        }
    }

    public SessionState getSession(UUID sessionId) {
        String json = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, SessionState.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize session", e);
        }
    }

    public void deleteSession(UUID sessionId) {
        redisTemplate.delete(SESSION_PREFIX + sessionId);
    }
}
