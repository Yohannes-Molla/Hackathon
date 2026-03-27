package et.trustlayer.authserver.session;

import java.util.Map;

public record SessionView(
        String id,
        String ipAddress,
        long start,
        long lastAccess,
        Map<String, String> clients
) {
}
