package et.trustlayer.authserver.session;

import com.fasterxml.jackson.databind.JsonNode;
import et.trustlayer.authserver.config.KeycloakAdminProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class KeycloakSessionService {

    private final KeycloakAdminProperties properties;
    private final RestClient restClient = RestClient.create();

    public List<SessionView> getActiveSessions(String keycloakUserId) {
        JsonNode sessions = restClient.get()
                .uri(properties.baseUrl() + "/admin/realms/" + properties.realm() + "/users/" + keycloakUserId + "/sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + obtainAdminToken())
                .retrieve()
                .body(JsonNode.class);

        List<SessionView> views = new ArrayList<>();
        if (sessions == null || !sessions.isArray()) {
            return views;
        }

        for (JsonNode session : sessions) {
            Map<String, String> clients = new HashMap<>();
            JsonNode clientsNode = session.path("clients");
            if (clientsNode.isObject()) {
                clientsNode.fields().forEachRemaining(entry -> clients.put(entry.getKey(), entry.getValue().asText("")));
            }
            views.add(new SessionView(
                    session.path("id").asText(""),
                    session.path("ipAddress").asText(""),
                    session.path("start").asLong(0),
                    session.path("lastAccess").asLong(0),
                    clients
            ));
        }
        return views;
    }

    public boolean revokeSessionIfOwnedByUser(String keycloakUserId, String sessionId) {
        boolean ownedByUser = getActiveSessions(keycloakUserId).stream()
                .anyMatch(session -> session.id().equals(sessionId));

        if (!ownedByUser) {
            return false;
        }

        restClient.delete()
                .uri(properties.baseUrl() + "/admin/realms/" + properties.realm() + "/sessions/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + obtainAdminToken())
                .retrieve()
                .toBodilessEntity();

        return true;
    }

    private String obtainAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        String tokenRealm = properties.realm();
        if (hasText(properties.adminUsername()) && hasText(properties.adminPassword())) {
            form.add("grant_type", "password");
            form.add("client_id", hasText(properties.adminClientId()) ? properties.adminClientId() : "admin-cli");
            form.add("username", properties.adminUsername());
            form.add("password", properties.adminPassword());
            tokenRealm = hasText(properties.adminRealm()) ? properties.adminRealm() : "master";
        } else {
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.adminClientId());
            form.add("client_secret", properties.adminClientSecret());
        }

        try {
            JsonNode tokenResponse = restClient.post()
                    .uri(properties.baseUrl() + "/realms/" + tokenRealm + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (tokenResponse == null || tokenResponse.path("access_token").isMissingNode()) {
                throw new IllegalStateException("Keycloak admin token response missing access_token");
            }
            return tokenResponse.path("access_token").asText();
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("Failed to obtain Keycloak admin token: " + ex.getResponseBodyAsString(), ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
