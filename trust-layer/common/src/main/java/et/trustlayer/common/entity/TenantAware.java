package et.trustlayer.common.entity;

import java.util.UUID;

public interface TenantAware {
    void setTenantId(UUID tenantId);
    UUID getTenantId();
}
