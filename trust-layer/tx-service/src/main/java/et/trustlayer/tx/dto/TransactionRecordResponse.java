package et.trustlayer.tx.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRecordResponse {
    private String txId;
    private String status;
    private String merchantId;
    private Long amountMinor;
    private String currency;
    private String timestamp;
}
