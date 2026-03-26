package et.trustlayer.vci.controller;

import et.trustlayer.vci.dto.ProvisionCardRequest;
import et.trustlayer.vci.dto.VirtualCardResponse;
import et.trustlayer.vci.service.VirtualCardService;
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
}
