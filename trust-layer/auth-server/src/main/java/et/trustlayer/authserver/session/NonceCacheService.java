package et.trustlayer.authserver.session;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NonceCacheService {
    private static final String NONCE_PREFIX = "tl:dpop:nonce:";
    private final StringRedisTemplate redisTemplate;

    public UUID generateNonce() {
        UUID nonce = UUID.randomUUID();
        redisTemplate.opsForValue().set(NONCE_PREFIX + nonce, "1", Duration.ofSeconds(30));
        return nonce;
    }

    public boolean consumeNonce(String nonce) {
        // GETDEL equivalent if Redis 6.2+. 
        // Spring Data Redis stringRedisTemplate.delete returns boolean, 
        // but to ensure atomic GET + DEL we can use a script or delete if it existed.
        return Boolean.TRUE.equals(redisTemplate.delete(NONCE_PREFIX + nonce));
    }
}
