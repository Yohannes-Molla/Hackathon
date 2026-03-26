package et.trustlayer.authserver.dpop;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DPoPValidationResult {
    private final boolean valid;
    private final String error;
    private final String jwkThumbprint;

    public static DPoPValidationResult valid(String jwkThumbprint) {
        return new DPoPValidationResult(true, null, jwkThumbprint);
    }

    public static DPoPValidationResult invalid(String error) {
        return new DPoPValidationResult(false, error, null);
    }
}
