package et.trustlayer.vci.repository;

import et.trustlayer.common.entity.BiometricCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BiometricCredentialRepository extends JpaRepository<BiometricCredential, UUID> {
    Optional<BiometricCredential> findByKeyId(String keyId);
}
