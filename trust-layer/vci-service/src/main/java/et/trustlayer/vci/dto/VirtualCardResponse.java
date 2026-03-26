package et.trustlayer.vci.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VirtualCardResponse {
    private String cardId;
    private String lastFour;
    private String cardNetwork;
    private String status;
    private String currency;
    private Long dailyLimitMinor;
}
