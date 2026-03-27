package et.trustlayer.vci.controller;

import et.trustlayer.vci.dto.ProvisionCardRequest;
import et.trustlayer.vci.dto.VirtualCardResponse;
import et.trustlayer.vci.service.VirtualCardService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/vci")
@RequiredArgsConstructor
@Slf4j
public class VirtualCardController {

    private final VirtualCardService virtualCardService;

    @PostMapping("/provision")
    public ResponseEntity<?> provisionCard(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestBody ProvisionCardRequest request) {

        try {
            VirtualCardResponse response = virtualCardService.provisionCard(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Virtual Card Provisioning Failed", e);
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/cards/{userId}")
    public ResponseEntity<List<VirtualCardResponse>> getUserCards(
            @PathVariable String userId) {
        try {
            List<VirtualCardResponse> cards = virtualCardService.getUserCards(userId);
            return ResponseEntity.ok(cards);
        } catch (Exception e) {
            log.error("Failed to fetch user cards", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/cards/{cardId}/cvv")
    public ResponseEntity<Map<String, String>> getDynamicCvv(@PathVariable String cardId) {
        try {
            return ResponseEntity.ok(Map.of("cardId", cardId, "cvv", virtualCardService.getCurrentDynamicCvv(cardId)));
        } catch (Exception e) {
            log.error("Failed to get dynamic CVV", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/cards/{cardId}/limits")
    public ResponseEntity<?> updateLimits(
            @PathVariable String cardId,
            @RequestBody Map<String, Long> request) {
        try {
            VirtualCardResponse response = virtualCardService.updateLimits(
                cardId,
                request.get("dailyLimitMinor"),
                request.get("singleTxLimitMinor")
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update card limits", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/cards/{cardId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String cardId,
            @RequestBody Map<String, String> request) {
        try {
            VirtualCardResponse response = virtualCardService.updateStatus(cardId, request.getOrDefault("status", "ACTIVE"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update card status", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/merchants/register")
    public ResponseEntity<?> registerMerchant(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody MerchantRegisterRequest request) {
        try {
            var merchant = virtualCardService.registerMerchant(tenantId, request.keycloakSub(), request.name(), request.category());
            return ResponseEntity.ok(Map.of(
                "id", merchant.getId().toString(),
                "name", merchant.getName(),
                "category", merchant.getCategory(),
                "status", merchant.getStatus()
            ));
        } catch (Exception e) {
            log.error("Failed to register merchant", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/merchants/{merchantId}")
    public ResponseEntity<?> getMerchant(@PathVariable String merchantId) {
        try {
            var merchant = virtualCardService.getMerchant(merchantId);
            return ResponseEntity.ok(Map.of(
                "id", merchant.getId().toString(),
                "name", merchant.getName(),
                "category", merchant.getCategory(),
                "status", merchant.getStatus(),
                "onboardingState", merchant.getOnboardingState()
            ));
        } catch (Exception e) {
            log.error("Failed to get merchant", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private record MerchantRegisterRequest(
            @NotBlank String keycloakSub,
            @NotBlank String name,
            @NotBlank String category
    ) {}
}
