package et.trustlayer.authserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trustlayer.keycloak")
public record KeycloakAdminProperties(
        String baseUrl,
        String realm,
        String adminClientId,
        String adminClientSecret,
        String adminUsername,
        String adminPassword,
        String adminRealm
) {
}
