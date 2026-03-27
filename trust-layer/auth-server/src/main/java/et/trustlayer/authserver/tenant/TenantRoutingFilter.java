package et.trustlayer.authserver.tenant;

import et.trustlayer.authserver.repository.TenantRepository;
import et.trustlayer.common.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class TenantRoutingFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String headerTenant = request.getHeader("X-Tenant-ID");
            if (headerTenant != null && !headerTenant.isEmpty()) {
                TenantContext.setTenantId(UUID.fromString(headerTenant));
            } else {
                String host = firstNonBlank(
                        request.getHeader("X-Forwarded-Host"),
                        request.getHeader("Host"),
                        request.getServerName());
                if (host != null && host.contains(":")) {
                    host = host.substring(0, host.indexOf(':'));
                }
                Optional<String> slugFromSubdomain = Optional.empty();
                if (host != null && !host.equalsIgnoreCase("localhost") && !host.equals("127.0.0.1")) {
                    String[] parts = host.split("\\.");
                    if (parts.length >= 3) {
                        slugFromSubdomain = Optional.of(parts[0]);
                    }
                }
                var tenant = slugFromSubdomain.flatMap(tenantRepository::findBySlug);
                if (tenant.isEmpty()) {
                    tenant = tenantRepository.findBySlug("hub");
                }
                tenant.ifPresent(t -> TenantContext.setTenantId(t.getId()));
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
