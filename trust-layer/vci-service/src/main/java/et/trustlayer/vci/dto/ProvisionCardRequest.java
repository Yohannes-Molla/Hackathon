package et.trustlayer.vci.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionCardRequest {
    private String userId;
    private String keyId; // The ID of the BiometricCredential to bind the card to
}
