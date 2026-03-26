package et.trustlayer.common.security;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> CONTEXT = new ThreadLocal<>();

    public static void setTenantId(UUID tenantId) {
        CONTEXT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
