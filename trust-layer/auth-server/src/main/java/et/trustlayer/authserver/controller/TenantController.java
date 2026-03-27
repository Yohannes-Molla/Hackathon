package et.trustlayer.authserver.controller;

import et.trustlayer.authserver.repository.TenantRepository;
import et.trustlayer.common.entity.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;

    @GetMapping
    public ResponseEntity<List<TenantSummaryResponse>> listTenants() {
        List<TenantSummaryResponse> tenants = tenantRepository.findAll()
            .stream()
            .map(TenantSummaryResponse::from)
            .toList();
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getTenant(@PathVariable("slug") String slug) {
        return tenantRepository.findBySlug(slug)
            .<ResponseEntity<?>>map(tenant -> ResponseEntity.ok(TenantDetailResponse.from(tenant)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tenant not found"));
    }

    @GetMapping("/{slug}/branding")
    public ResponseEntity<?> getTenantBranding(@PathVariable("slug") String slug) {
        return tenantRepository.findBySlug(slug)
            .<ResponseEntity<?>>map(tenant -> ResponseEntity.ok(TenantBrandingResponse.from(tenant)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tenant not found"));
    }

    @PostMapping
    public ResponseEntity<?> createTenant(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TenantUpsertRequest request) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }

        Optional<Tenant> existing = tenantRepository.findBySlug(request.slug());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Tenant slug already exists");
        }

        Tenant tenant = Tenant.builder()
            .name(request.name())
            .slug(request.slug())
            .domain(request.domain())
            .primaryColor(request.primaryColor() == null ? "#6366f1" : request.primaryColor())
            .logoUrl(request.logoUrl())
            .customCss(request.customCss())
            .trustFramework(request.trustFramework() == null ? "et_nbe_kyc" : request.trustFramework())
            .hub(Boolean.TRUE.equals(request.hub()))
            .build();

        Tenant saved = tenantRepository.save(tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantDetailResponse.from(saved));
    }

    @PutMapping("/{slug}")
    public ResponseEntity<?> updateTenant(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("slug") String slug,
        @Valid @RequestBody TenantUpsertRequest request
    ) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }

        Optional<Tenant> tenantOptional = tenantRepository.findBySlug(slug);
        if (tenantOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tenant not found");
        }

        Tenant tenant = tenantOptional.get();
        if (!tenant.getSlug().equals(request.slug())) {
            Optional<Tenant> targetSlug = tenantRepository.findBySlug(request.slug());
            if (targetSlug.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Tenant slug already exists");
            }
        }

        tenant.setName(request.name());
        tenant.setSlug(request.slug());
        tenant.setDomain(request.domain());
        tenant.setPrimaryColor(request.primaryColor() == null ? tenant.getPrimaryColor() : request.primaryColor());
        tenant.setLogoUrl(request.logoUrl());
        tenant.setCustomCss(request.customCss());
        tenant.setTrustFramework(request.trustFramework() == null ? tenant.getTrustFramework() : request.trustFramework());
        tenant.setHub(Boolean.TRUE.equals(request.hub()));

        Tenant updated = tenantRepository.save(tenant);
        return ResponseEntity.ok(TenantDetailResponse.from(updated));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<?> deleteTenant(@AuthenticationPrincipal Jwt jwt, @PathVariable("slug") String slug) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin role required");
        }

        Optional<Tenant> tenantOptional = tenantRepository.findBySlug(slug);
        if (tenantOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tenant not found");
        }

        tenantRepository.delete(tenantOptional.get());
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Jwt jwt) {
        Object realmAccessObj = jwt.getClaims().get("realm_access");
        if (realmAccessObj instanceof Map<?, ?> realmAccess) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List<?> roles) {
                for (Object role : roles) {
                    if ("admin".equals(role)) {
                        return true;
                    }
                }
            }
        }

        Object scopeObj = jwt.getClaims().get("scope");
        if (scopeObj instanceof String scope) {
            return List.of(scope.split("\\s+")).contains("admin");
        }
        return false;
    }

    @Builder
    private record TenantSummaryResponse(
        String name,
        String slug,
        String domain
    ) {
        private static TenantSummaryResponse from(Tenant tenant) {
            return TenantSummaryResponse.builder()
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .domain(tenant.getDomain())
                .build();
        }
    }

    @Builder
    private record TenantDetailResponse(
        String id,
        String name,
        String slug,
        String domain,
        String primaryColor,
        String logoUrl,
        String customCss,
        String trustFramework,
        boolean hub
    ) {
        private static TenantDetailResponse from(Tenant tenant) {
            return TenantDetailResponse.builder()
                .id(tenant.getId() == null ? null : tenant.getId().toString())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .domain(tenant.getDomain())
                .primaryColor(tenant.getPrimaryColor())
                .logoUrl(tenant.getLogoUrl())
                .customCss(tenant.getCustomCss())
                .trustFramework(tenant.getTrustFramework())
                .hub(tenant.isHub())
                .build();
        }
    }

    @Builder
    private record TenantBrandingResponse(
        String name,
        String slug,
        String primaryColor,
        String logoUrl
    ) {
        private static TenantBrandingResponse from(Tenant tenant) {
            return TenantBrandingResponse.builder()
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .primaryColor(tenant.getPrimaryColor())
                .logoUrl(tenant.getLogoUrl())
                .build();
        }
    }

    private record TenantUpsertRequest(
        @NotBlank String name,
        @NotBlank String slug,
        String domain,
        String primaryColor,
        String logoUrl,
        String customCss,
        String trustFramework,
        Boolean hub
    ) {
    }
}
