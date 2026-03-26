package et.trustlayer.authserver.session;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.UUID;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionState {
    private String state; // PENDING_EKYC, EKYC_COMPLETE, CREDENTIAL_BOUND
    private UUID tenantId;
    private UUID userId;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public SessionState(UUID tenantId, String state) {
        this.tenantId = tenantId;
        this.state = state;
        this.createdAt = LocalDateTime.now();
    }
}
