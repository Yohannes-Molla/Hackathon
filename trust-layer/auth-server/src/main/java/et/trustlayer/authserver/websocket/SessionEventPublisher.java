package et.trustlayer.authserver.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishEvent(String sessionId, String eventType, String message) {
        SessionEvent event = new SessionEvent(sessionId, eventType, message);
        messagingTemplate.convertAndSend("/topic/session/" + sessionId, event);
    }

    public void publishEkycComplete(String sessionId) {
        publishEvent(sessionId, "EKYC_COMPLETE", "eKYC verification completed successfully");
    }

    public void publishCredentialBound(String sessionId) {
        publishEvent(sessionId, "CREDENTIAL_BOUND", "Biometric credential registered and bound");
    }

    public void publishTxApproved(String sessionId, String txId) {
        publishEvent(sessionId, "TX_APPROVED", "Transaction " + txId + " approved");
    }

    public void publishTxRejected(String sessionId, String txId) {
        publishEvent(sessionId, "TX_REJECTED", "Transaction " + txId + " rejected");
    }
}
