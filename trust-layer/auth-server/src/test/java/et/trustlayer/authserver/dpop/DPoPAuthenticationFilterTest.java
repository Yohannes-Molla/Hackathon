package et.trustlayer.authserver.dpop;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DPoPAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingDpopHeaderWhenJwtAuthenticated() throws ServletException, IOException {
        DPoPProofValidator validator = mock(DPoPProofValidator.class);
        DPoPAuthenticationFilter filter = new DPoPAuthenticationFilter(validator);
        authenticateWithJwt("token-value", "thumb-1");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/credentials/register");
        request.setRequestURI("/api/credentials/register");
        request.setServerName("localhost");
        request.setScheme("http");
        request.setServerPort(9000);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsInvalidProofFromValidator() throws ServletException, IOException {
        DPoPProofValidator validator = mock(DPoPProofValidator.class);
        when(validator.validate("bad-proof", "POST", "http://localhost:9000/api/credentials/register", "token-value"))
                .thenReturn(DPoPValidationResult.invalid("bad signature"));
        DPoPAuthenticationFilter filter = new DPoPAuthenticationFilter(validator);
        authenticateWithJwt("token-value", "thumb-1");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/credentials/register");
        request.setRequestURI("/api/credentials/register");
        request.setServerName("localhost");
        request.setScheme("http");
        request.setServerPort(9000);
        request.addHeader("DPoP", "bad-proof");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void allowsValidProofWithMatchingCnfThumbprint() throws ServletException, IOException {
        DPoPProofValidator validator = mock(DPoPProofValidator.class);
        when(validator.validate("good-proof", "POST", "http://localhost:9000/api/credentials/register", "token-value"))
                .thenReturn(DPoPValidationResult.valid("thumb-1"));
        DPoPAuthenticationFilter filter = new DPoPAuthenticationFilter(validator);
        authenticateWithJwt("token-value", "thumb-1");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/credentials/register");
        request.setRequestURI("/api/credentials/register");
        request.setServerName("localhost");
        request.setScheme("http");
        request.setServerPort(9000);
        request.addHeader("DPoP", "good-proof");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    private void authenticateWithJwt(String tokenValue, String jkt) {
        Jwt jwt = new Jwt(
                tokenValue,
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", "user-1", "cnf", Map.of("jkt", jkt))
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
