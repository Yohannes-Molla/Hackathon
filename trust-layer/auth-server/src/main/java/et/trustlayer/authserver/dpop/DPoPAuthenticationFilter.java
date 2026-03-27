package et.trustlayer.authserver.dpop;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.RequiredArgsConstructor;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DPoPAuthenticationFilter extends OncePerRequestFilter {

    private final DPoPProofValidator proofValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String dpopHeader = request.getHeader("DPoP");
            if (dpopHeader == null || dpopHeader.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing DPoP header");
                return;
            }

            String method = request.getMethod();
            String uri = request.getRequestURL().toString();
            String accessToken = jwtAuth.getToken().getTokenValue();

            DPoPValidationResult result = proofValidator.validate(dpopHeader, method, uri, accessToken);
            if (!result.isValid()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid DPoP proof: " + result.getError());
                return;
            }

            // Verify cnf binding in the JWT
            Object cnfClaim = jwtAuth.getToken().getClaims().get("cnf");
            if (cnfClaim instanceof java.util.Map cnfMap) {
                if (!result.getJwkThumbprint().equals(cnfMap.get("jkt"))) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "DPoP Thumbprint mismatch");
                    return;
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access token missing 'cnf' claim for DPoP binding");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
