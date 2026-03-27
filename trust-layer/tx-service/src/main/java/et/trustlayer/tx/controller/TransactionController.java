package et.trustlayer.tx.controller;

import et.trustlayer.tx.dto.SignedTransactionRequest;
import et.trustlayer.tx.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/tx")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitTransaction(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @RequestHeader(value = "DPoP", required = false) String dpopProof,
            @RequestBody SignedTransactionRequest request) {

        try {
            log.info("Received signed transaction for processing");
            Map<String, Object> result = transactionService.verifyAndSubmitTransaction(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Transaction Submit Failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "REJECTED");
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/pending-challenges")
    public ResponseEntity<List<Map<String, Object>>> pendingChallenges(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(transactionService.listPendingChallenges(jwt.getSubject()));
    }

    @PostMapping("/approve-web")
    public ResponseEntity<Map<String, Object>> approveWeb(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {
        try {
            String nonce = body != null ? body.get("nonce") : null;
            if (nonce == null || nonce.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "nonce required"));
            }
            Map<String, Object> result = transactionService.approveWebTransaction(jwt.getSubject(), nonce);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Web transaction approval failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "REJECTED");
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiateTransaction(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestBody et.trustlayer.tx.dto.InitiateTransactionRequest request) {
        try {
            log.info("Initiating transaction for user {}", request.getUserId());
            Map<String, Object> result = transactionService.initiateTransaction(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to initiate transaction", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<et.trustlayer.tx.dto.TransactionRecordResponse>> getTransactionHistory(
            @PathVariable String userId) {
        try {
            List<et.trustlayer.tx.dto.TransactionRecordResponse> history = transactionService.getTransactionHistory(userId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Failed to get transaction history", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/merchant/{merchantId}/history")
    public ResponseEntity<List<et.trustlayer.tx.dto.TransactionRecordResponse>> getMerchantTransactionHistory(
            @PathVariable String merchantId) {
        try {
            List<et.trustlayer.tx.dto.TransactionRecordResponse> history = transactionService.getMerchantTransactionHistory(merchantId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Failed to get merchant transaction history", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/merchant/{merchantId}/reconciliation")
    public ResponseEntity<Map<String, Object>> getMerchantReconciliation(
            @PathVariable String merchantId) {
        try {
            List<et.trustlayer.tx.dto.TransactionRecordResponse> history = transactionService.getMerchantTransactionHistory(merchantId);
            long approvedMinor = history.stream()
                    .filter(tx -> "APPROVED".equalsIgnoreCase(tx.getStatus()))
                    .mapToLong(tx -> tx.getAmountMinor() == null ? 0L : tx.getAmountMinor())
                    .sum();
            Map<String, Object> response = new HashMap<>();
            response.put("merchantId", merchantId);
            response.put("approvedCount", history.stream().filter(tx -> "APPROVED".equalsIgnoreCase(tx.getStatus())).count());
            response.put("approvedAmountMinor", approvedMinor);
            response.put("currency", history.stream().findFirst().map(et.trustlayer.tx.dto.TransactionRecordResponse::getCurrency).orElse("ETB"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get merchant reconciliation", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
