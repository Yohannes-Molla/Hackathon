package et.trustlayer.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CredentialRegistrationRequest {
    private String jwk;
    private String attestationCertChain;
    private String deviceId;
}
