package et.trustlayer.authserver.controller;

import et.trustlayer.authserver.session.KeycloakSessionService;
import et.trustlayer.authserver.session.SessionView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final KeycloakSessionService keycloakSessionService;

    @GetMapping("/active")
    public ResponseEntity<List<SessionView>> activeSessions(@AuthenticationPrincipal Jwt jwt) {
        String keycloakUserId = jwt.getSubject();
        return ResponseEntity.ok(keycloakSessionService.getActiveSessions(keycloakUserId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") String sessionId) {
        String keycloakUserId = jwt.getSubject();
        boolean revoked = keycloakSessionService.revokeSessionIfOwnedByUser(keycloakUserId, sessionId);
        if (!revoked) {
            return ResponseEntity.status(404).body("Session not found for current user");
        }
        return ResponseEntity.noContent().build();
    }
}
