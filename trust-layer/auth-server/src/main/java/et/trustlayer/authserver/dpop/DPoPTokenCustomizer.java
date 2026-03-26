package et.trustlayer.authserver.dpop;

import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class DPoPTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        String jwkThumbprint = getJwkThumbprintFromRequest(context);
        if (jwkThumbprint != null) {
            Map<String, Object> cnf = new HashMap<>();
            cnf.put("jkt", jwkThumbprint);
            context.getClaims().claim("cnf", cnf);
        }
    }

    private String getJwkThumbprintFromRequest(JwtEncodingContext context) {
        // Implementation logic depends on how the DPoP header is passed during token request.
        // It could be extracted from a context attribute set by a DPoPAuthenticationFilter for the token endpoint
        // or directly from registered client credentials if using MTLS. 
        // For demonstration, we'll try fetching it from Authorization mechanism attributes.
        if (context.getAuthorization() == null) return null;
        Object thumbprintObj = context.getAuthorization().getAttribute("DPOP_JWK_THUMBPRINT");
        return thumbprintObj instanceof String ? (String) thumbprintObj : null;
    }
}
