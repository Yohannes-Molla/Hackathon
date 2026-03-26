package et.trustlayer.authserver.repository;

import et.trustlayer.common.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    Optional<UserIdentity> findByExternalSub(String externalSub);
}
