package et.trustlayer.authserver.controller;

import et.trustlayer.authserver.identity.UserIdentityLifecycleService;
import et.trustlayer.common.entity.UserIdentity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final UserIdentityLifecycleService userIdentityLifecycleService;

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
        Optional<UserIdentity> identity = userIdentityLifecycleService.syncAndResolve(jwt);
        if (identity.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Identity not found and insufficient claims to bootstrap profile");
        }
        return ResponseEntity.ok(IdentityMeResponse.from(identity.get()));
    }

    @Builder
    private record IdentityMeResponse(
        UUID id,
        UUID tenantId,
        String keycloakSub,
        String externalSub,
        String givenName,
        String familyName,
        LocalDate dateOfBirth,
        String nationality,
        String assuranceLevel,
        String onboardingState
    ) {
        private static IdentityMeResponse from(UserIdentity identity) {
            return IdentityMeResponse.builder()
                .id(identity.getId())
                .tenantId(identity.getTenant() == null ? null : identity.getTenant().getId())
                .keycloakSub(identity.getKeycloakSub())
                .externalSub(identity.getExternalSub())
                .givenName(identity.getGivenName())
                .familyName(identity.getFamilyName())
                .dateOfBirth(identity.getDateOfBirth())
                .nationality(identity.getNationality())
                .assuranceLevel(identity.getAssuranceLevel())
                .onboardingState(identity.getOnboardingState())
                .build();
        }
    }
}
