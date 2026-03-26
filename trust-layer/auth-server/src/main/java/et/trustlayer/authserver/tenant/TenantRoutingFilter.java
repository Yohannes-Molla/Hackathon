package et.trustlayer.authserver.tenant;

import et.trustlayer.common.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class TenantRoutingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // First check Header X-Tenant-ID
            String headerTenant = request.getHeader("X-Tenant-ID");
            if (headerTenant != null && !headerTenant.isEmpty()) {
                TenantContext.setTenantId(UUID.fromString(headerTenant));
            } else {
                // Check subdomain e.g. banka.trustlayer.et
                String serverName = request.getServerName();
                String slug = serverName.split("\\.")[0];
                if (!"hub".equals(slug) && !slug.equals("localhost")) {
                    // Requires an injection of TenantRepository to lookup the UUID by slug
                    // Simplified for demo : TenantContext.setTenantId(...) lookup
                }
            }

            // MTLS extraction typically done via SecurityContext after authentication,
            // so this is a simplified initial routing filter.

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
