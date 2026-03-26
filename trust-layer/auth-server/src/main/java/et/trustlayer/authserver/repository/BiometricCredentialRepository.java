package et.trustlayer.authserver.repository;

import et.trustlayer.common.entity.BiometricCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BiometricCredentialRepository extends JpaRepository<BiometricCredential, UUID> {
    Optional<BiometricCredential> findByKeyId(String keyId);
    Optional<BiometricCredential> findByJwkThumbprint(String jwkThumbprint);
}
