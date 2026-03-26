package et.trustlayer.common.entity;

import et.trustlayer.common.security.TenantContext;
import org.springframework.stereotype.Component;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.lang.reflect.Field;
import java.util.UUID;

@Component
public class TenantAwareEntityListener {

    @PrePersist
    @PreUpdate
    public void setTenantId(Object entity) {
        UUID currentTenantId = TenantContext.getTenantId();
        if (currentTenantId != null) {
            try {
                // Assuming entity has a tenant or tenant_id field, simplifying via reflection for generic listener
                // In actual Spring Data JPA, normally you use an interface like TenantAware and cast it.
                if (entity instanceof TenantAware) {
                    ((TenantAware) entity).setTenantId(currentTenantId);
                }
            } catch (Exception e) {
                // Ignore if it's not applicable
            }
        }
    }
}
