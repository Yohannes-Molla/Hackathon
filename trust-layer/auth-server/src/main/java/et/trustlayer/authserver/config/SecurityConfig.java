package et.trustlayer.authserver.config;

import et.trustlayer.authserver.dpop.DPoPAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final DPoPAuthenticationFilter dPoPAuthenticationFilter;

    public SecurityConfig(DPoPAuthenticationFilter dPoPAuthenticationFilter) {
        this.dPoPAuthenticationFilter = dPoPAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/ws/**")
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt())
            .addFilterAfter(dPoPAuthenticationFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
