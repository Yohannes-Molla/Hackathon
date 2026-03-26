package et.trustlayer.authserver.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEvent {
    private String sessionId;
    private String eventType; // EKYC_COMPLETE, CREDENTIAL_BOUND, TX_APPROVED, TX_REJECTED
    private String message;
}
