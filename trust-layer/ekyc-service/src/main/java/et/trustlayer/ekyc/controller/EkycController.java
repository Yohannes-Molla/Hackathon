package et.trustlayer.ekyc.controller;

import et.trustlayer.ekyc.service.EkycVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/ekyc")
@RequiredArgsConstructor
@Slf4j
public class EkycController {

    private final EkycVerificationService verificationService;

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> uploadEkycDocument(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestParam("sessionId") String sessionId,
            @RequestPart("documentImage") MultipartFile documentImage,
            @RequestPart(value = "livenessFrames", required = false) MultipartFile livenessFrames) {

        try {
            log.info("Received verification request for session {}", sessionId);
            Map<String, Object> response = verificationService.verifyDocument(sessionId, documentImage, livenessFrames);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("eKYC Verification Failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "REJECTED");
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/status/{sessionId}")
    public ResponseEntity<Map<String, Object>> getVerificationStatus(@PathVariable String sessionId) {
        try {
            Map<String, Object> response = verificationService.getVerificationStatus(sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get eKYC status for session {}", sessionId, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/claims/{userId}")
    public ResponseEntity<Map<String, Object>> getClaims(@PathVariable String userId) {
        try {
            Map<String, Object> response = verificationService.getVerifiedClaims(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get eKYC claims for user {}", userId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
