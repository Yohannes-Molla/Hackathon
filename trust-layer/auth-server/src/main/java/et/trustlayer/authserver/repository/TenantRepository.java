package et.trustlayer.authserver.repository;

import et.trustlayer.common.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByMtlsCertThumbprint(String mtlsCertThumbprint);
}
