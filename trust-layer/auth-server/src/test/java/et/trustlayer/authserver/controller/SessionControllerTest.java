package et.trustlayer.authserver.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import et.trustlayer.authserver.config.SecurityConfig;
import et.trustlayer.authserver.dpop.DPoPValidationResult;
import et.trustlayer.authserver.dpop.DPoPProofValidator;
import et.trustlayer.authserver.repository.TenantRepository;
import et.trustlayer.authserver.repository.UserIdentityRepository;
import et.trustlayer.common.entity.Tenant;
import et.trustlayer.authserver.service.AuditService;
import et.trustlayer.authserver.session.KeycloakSessionService;
import et.trustlayer.authserver.session.SessionView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SessionController.class)
@Import(SecurityConfig.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KeycloakSessionService keycloakSessionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private DPoPProofValidator dpopProofValidator;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private AuditService auditService;

    @MockBean
    private UserIdentityRepository userIdentityRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUpRedisMocks() {
        Tenant hub = Tenant.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .name("Trust Layer Hub")
                .slug("hub")
                .trustFramework("et_nbe_kyc")
                .hub(true)
                .build();
        given(tenantRepository.findBySlug(any(String.class))).willReturn(Optional.of(hub));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(anyString())).willReturn(1L);
        given(stringRedisTemplate.getExpire(anyString())).willReturn(60L);
        given(dpopProofValidator.validate(anyString(), anyString(), anyString(), anyString()))
                .willReturn(DPoPValidationResult.valid("thumb-1"));
    }

    @Test
    void activeSessionsReturnsCurrentUserSessions() throws Exception {
        SessionView session = new SessionView(
                "sess-1",
                "127.0.0.1",
                1710000000L,
                1710001111L,
                Map.of("trust-layer-web", "Trust Layer Web")
        );
        given(keycloakSessionService.getActiveSessions("user-sub-1")).willReturn(List.of(session));

        mockMvc.perform(get("/api/sessions/active")
                        .header("DPoP", "proof")
                        .with(jwt().jwt(jwt -> jwt.subject("user-sub-1").claim("cnf", Map.of("jkt", "thumb-1")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sess-1"))
                .andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$[0].clients['trust-layer-web']").value("Trust Layer Web"));

        verify(keycloakSessionService).getActiveSessions("user-sub-1");
    }

    @Test
    void revokeSessionReturnsNoContentWhenOwnedByUser() throws Exception {
        given(keycloakSessionService.revokeSessionIfOwnedByUser("user-sub-2", "sess-2")).willReturn(true);

        mockMvc.perform(delete("/api/sessions/sess-2")
                        .header("DPoP", "proof")
                        .with(jwt().jwt(jwt -> jwt.subject("user-sub-2").claim("cnf", Map.of("jkt", "thumb-1")))))
                .andExpect(status().isNoContent());

        verify(keycloakSessionService).revokeSessionIfOwnedByUser("user-sub-2", "sess-2");
    }

    @Test
    void revokeSessionReturnsNotFoundWhenSessionNotOwnedByUser() throws Exception {
        given(keycloakSessionService.revokeSessionIfOwnedByUser(anyString(), eq("missing-session"))).willReturn(false);

        mockMvc.perform(delete("/api/sessions/missing-session")
                        .header("DPoP", "proof")
                        .with(jwt().jwt(jwt -> jwt.subject("user-sub-3").claim("cnf", Map.of("jkt", "thumb-1")))))
                .andExpect(status().isNotFound());

        verify(keycloakSessionService).revokeSessionIfOwnedByUser("user-sub-3", "missing-session");
    }

    @Test
    void activeSessionsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/sessions/active"))
                .andExpect(status().isUnauthorized());
    }
}
