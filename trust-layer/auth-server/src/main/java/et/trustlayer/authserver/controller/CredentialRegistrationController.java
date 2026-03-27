package et.trustlayer.authserver.controller;

import com.nimbusds.jose.jwk.JWK;
import et.trustlayer.authserver.repository.BiometricCredentialRepository;
import et.trustlayer.authserver.repository.TenantRepository;
import et.trustlayer.authserver.repository.UserIdentityRepository;
import et.trustlayer.authserver.service.AuditService;
import et.trustlayer.authserver.service.KeyAttestationService;
import et.trustlayer.common.dto.CredentialRegistrationRequest;
import et.trustlayer.common.entity.BiometricCredential;
import et.trustlayer.common.entity.Tenant;
import et.trustlayer.common.entity.UserIdentity;
import et.trustlayer.common.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
@Slf4j
public class CredentialRegistrationController {

    private final BiometricCredentialRepository credentialRepository;
    private final UserIdentityRepository userRepository;
    private final TenantRepository tenantRepository;
    private final KeyAttestationService attestationService;
    private final AuditService auditService;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerCredential(@RequestBody CredentialRegistrationRequest request,
                                                @RequestHeader("X-User-ID") UUID userId) {
        try {
            JWK jwk = JWK.parse(request.getJwk());
            
            if (!attestationService.verifyAttestation(request.getAttestationCertChain(), jwk)) {
                return ResponseEntity.status(400).body("Invalid attestation");
            }

            String jwkThumbprint = jwk.computeThumbprint().toString();

            UserIdentity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Tenant tenant = user.getTenant();
            if (tenant == null && TenantContext.getTenantId() != null) {
                tenant = tenantRepository.findById(TenantContext.getTenantId())
                        .orElseThrow(() -> new RuntimeException("Tenant not found"));
            }
            if (tenant == null) {
                throw new RuntimeException("Tenant not found for user");
            }

            BiometricCredential credential = new BiometricCredential();
            credential.setKeyId(UUID.randomUUID().toString());
            credential.setPublicKeyJwk(request.getJwk());
            credential.setJwkThumbprint(jwkThumbprint);
            credential.setDeviceId(request.getDeviceId());
            credential.setAttestationCertificate(request.getAttestationCertChain());
            credential.setCredentialType("SIGNING");
            credential.setTenant(tenant);
            credential.setUserIdentity(user);
            credentialRepository.save(credential);

            auditService.log(tenant, userId.toString(), "CREDENTIAL_REGISTERED",
                    "BiometricCredential", credential.getKeyId(),
                    "{\"deviceId\":\"" + request.getDeviceId() + "\",\"credentialType\":\"SIGNING\"}");
            
            return ResponseEntity.ok(Map.of("keyId", credential.getKeyId()));
        } catch (ParseException | com.nimbusds.jose.JOSEException e) {
            return ResponseEntity.badRequest().body("Invalid JWK format or operation");
        }
    }
}
