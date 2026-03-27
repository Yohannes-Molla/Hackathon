package et.trustlayer.authserver.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.trustlayer.authserver.websocket.SessionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Subscribes to {@code tl:events:*} Redis channels and forwards payloads to the STOMP layer
 * via {@link SessionEventPublisher}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisSessionEventBridge implements MessageListener {

    public static final String CHAN_EKYC_COMPLETE = "tl:events:ekyc_complete";
    public static final String CHAN_CREDENTIAL_BOUND = "tl:events:credential_bound";
    public static final String CHAN_TX_APPROVED = "tl:events:tx_approved";
    public static final String CHAN_TX_REJECTED = "tl:events:tx_rejected";

    private final SessionEventPublisher sessionEventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
        try {
            switch (channel) {
                case CHAN_EKYC_COMPLETE -> sessionEventPublisher.publishEkycComplete(body);
                case CHAN_CREDENTIAL_BOUND -> sessionEventPublisher.publishCredentialBound(body);
                case CHAN_TX_APPROVED -> {
                    TxRouting r = parseTxRouting(body);
                    sessionEventPublisher.publishTxApproved(r.routingKey(), r.txId());
                }
                case CHAN_TX_REJECTED -> {
                    TxRouting r = parseTxRouting(body);
                    sessionEventPublisher.publishTxRejected(r.routingKey(), r.txId());
                }
                default -> log.warn("Ignoring message on unexpected channel: {}", channel);
            }
        } catch (Exception e) {
            log.error("Failed to bridge Redis message from channel {} body={}", channel, body, e);
        }
    }

    private TxRouting parseTxRouting(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            try {
                Map<String, Object> m = objectMapper.readValue(trimmed, new TypeReference<>() {});
                Object rk = m.get("sessionId");
                if (rk == null) {
                    rk = m.get("userId");
                }
                String txId = m.get("txId") != null ? m.get("txId").toString() : "unknown";
                if (rk != null) {
                    return new TxRouting(rk.toString(), txId);
                }
            } catch (Exception e) {
                log.warn("Could not parse tx event JSON; using raw body as routing key: {}", e.getMessage());
            }
        }
        return new TxRouting(trimmed, "unknown");
    }

    private record TxRouting(String routingKey, String txId) {}
}
