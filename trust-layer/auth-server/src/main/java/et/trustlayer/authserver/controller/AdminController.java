package et.trustlayer.authserver.controller;

import et.trustlayer.authserver.repository.TenantRepository;
import et.trustlayer.authserver.repository.TransactionRepository;
import et.trustlayer.authserver.repository.UserIdentityRepository;
import et.trustlayer.authserver.repository.AuditLogRepository;
import et.trustlayer.common.entity.AuditLog;
import et.trustlayer.common.entity.Transaction;
import et.trustlayer.common.entity.UserIdentity;
import java.time.ZoneOffset;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TenantRepository tenantRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/tenants")
    public ResponseEntity<?> getTenants(@AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }
        return ResponseEntity.ok(
            tenantRepository.findAll().stream().map(t -> TenantAdminView.builder()
                .id(t.getId() == null ? null : t.getId().toString())
                .slug(t.getSlug())
                .name(t.getName())
                .domain(t.getDomain())
                .build()).toList()
        );
    }

    @GetMapping("/tenants/{slug}/users")
    public ResponseEntity<?> getTenantUsers(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }
        List<UserIdentity> users = userIdentityRepository.findByTenantSlug(slug);
        return ResponseEntity.ok(users.stream().map(u -> UserAdminView.builder()
            .id(u.getId().toString())
            .givenName(u.getGivenName())
            .familyName(u.getFamilyName())
            .assuranceLevel(u.getAssuranceLevel())
            .onboardingState(u.getOnboardingState())
            .riskScore(u.getRiskScore())
            .build()).toList());
    }

    @GetMapping("/users/{userId}/transactions")
    public ResponseEntity<?> getUserTransactions(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }
        List<Transaction> txs = transactionRepository.findByUserIdentityIdOrderByCreatedAtDesc(java.util.UUID.fromString(userId));
        return ResponseEntity.ok(txs.stream().map(tx -> TxAdminView.builder()
            .txId(tx.getId().toString())
            .merchantId(tx.getMerchantId())
            .status(tx.getStatus())
            .amountMinor(tx.getAmountMinor())
            .currency(tx.getCurrency())
            .timestamp(tx.getCreatedAt().toInstant(ZoneOffset.UTC).toString())
            .build()).toList());
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String userId,
        @RequestBody UpdateStatusRequest request
    ) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }
        UserIdentity user = userIdentityRepository.findById(java.util.UUID.fromString(userId))
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setOnboardingState(request.status());
        userIdentityRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs(@AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }
        List<AuditLog> logs = auditLogRepository.findTop200ByOrderByCreatedAtDesc();
        return ResponseEntity.ok(logs.stream().map(log -> AuditLogView.builder()
            .id(log.getId())
            .actor(log.getActor())
            .action(log.getAction())
            .resourceType(log.getResourceType())
            .resourceId(log.getResourceId())
            .ipAddress(log.getIpAddress())
            .createdAt(log.getCreatedAt().toString())
            .build()).toList());
    }

    @GetMapping("/risk-summary")
    public ResponseEntity<?> getRiskSummary(@AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }
        List<UserIdentity> users = userIdentityRepository.findAll();
        long highRisk = users.stream().filter(u -> u.getRiskScore() != null && u.getRiskScore() >= 70).count();
        long mediumRisk = users.stream().filter(u -> u.getRiskScore() != null && u.getRiskScore() >= 40 && u.getRiskScore() < 70).count();
        long lowRisk = users.stream().filter(u -> u.getRiskScore() == null || u.getRiskScore() < 40).count();
        return ResponseEntity.ok(java.util.Map.of(
            "highRisk", highRisk,
            "mediumRisk", mediumRisk,
            "lowRisk", lowRisk,
            "totalUsers", users.size()
        ));
    }

    private boolean isAdmin(Jwt jwt) {
        Object realmAccessObj = jwt.getClaims().get("realm_access");
        if (realmAccessObj instanceof java.util.Map<?, ?> realmAccess) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof java.util.List<?> roles) {
                return roles.contains("admin");
            }
        }
        return false;
    }

    private record UpdateStatusRequest(String status) {}

    @Builder
    private record TenantAdminView(String id, String slug, String name, String domain) {}

    @Builder
    private record UserAdminView(
        String id,
        String givenName,
        String familyName,
        String assuranceLevel,
        String onboardingState,
        Integer riskScore
    ) {}

    @Builder
    private record TxAdminView(
        String txId,
        String merchantId,
        String status,
        Long amountMinor,
        String currency,
        String timestamp
    ) {}

    @Builder
    private record AuditLogView(
        Long id,
        String actor,
        String action,
        String resourceType,
        String resourceId,
        String ipAddress,
        String createdAt
    ) {}
}
