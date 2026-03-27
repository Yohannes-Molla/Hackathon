package et.trustlayer.authserver.identity;

import et.trustlayer.authserver.repository.TenantRepository;
import et.trustlayer.authserver.repository.UserIdentityRepository;
import et.trustlayer.common.entity.Tenant;
import et.trustlayer.common.entity.UserIdentity;
import et.trustlayer.common.security.TenantContext;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserIdentityLifecycleService {

    private final UserIdentityRepository userIdentityRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public Optional<UserIdentity> syncAndResolve(Jwt jwt) {
        String keycloakSub = jwt.getSubject();
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return Optional.empty();
        }

        Optional<UserIdentity> byKeycloakSub = userIdentityRepository.findByKeycloakSub(keycloakSub);
        if (byKeycloakSub.isPresent()) {
            return byKeycloakSub;
        }

        Optional<UserIdentity> byExternalSub = userIdentityRepository.findByExternalSub(keycloakSub);
        if (byExternalSub.isPresent()) {
            UserIdentity existing = byExternalSub.get();
            existing.setKeycloakSub(keycloakSub);
            return Optional.of(userIdentityRepository.save(existing));
        }

        return createFromJwt(jwt);
    }

    private Optional<UserIdentity> createFromJwt(Jwt jwt) {
        String givenName = claimAsString(jwt, "given_name");
        String familyName = claimAsString(jwt, "family_name");
        String birthRaw = claimAsString(jwt, "birthdate");
        LocalDate birthDate = parseDate(birthRaw);
        String nationality = claimAsString(jwt, "nationality");

        if (givenName == null || familyName == null) {
            return Optional.empty();
        }
        // Demo-friendly defaults when Keycloak omits optional profile claims
        if (birthDate == null) {
            birthDate = LocalDate.parse("1990-01-01");
        }
        if (nationality == null) {
            nationality = "ETH";
        }

        if (TenantContext.getTenantId() == null) {
            return Optional.empty();
        }

        Tenant tenant = tenantRepository.findById(TenantContext.getTenantId()).orElse(null);
        if (tenant == null) {
            return Optional.empty();
        }

        UserIdentity identity = UserIdentity.builder()
            .tenant(tenant)
            .externalSub(jwt.getSubject())
            .keycloakSub(jwt.getSubject())
            .givenName(givenName)
            .familyName(familyName)
            .dateOfBirth(birthDate)
            .nationality(nationality)
            .trustFramework(tenant.getTrustFramework())
            .onboardingState("PENDING")
            .build();

        return Optional.of(userIdentityRepository.save(identity));
    }

    private static String claimAsString(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return null;
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
