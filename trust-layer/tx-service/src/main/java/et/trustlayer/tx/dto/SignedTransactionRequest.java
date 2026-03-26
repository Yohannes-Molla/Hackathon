package et.trustlayer.tx.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignedTransactionRequest {
    private String payloadBase64;
    private String signatureBase64;
    private String keyId;
    private String algorithm;
}
