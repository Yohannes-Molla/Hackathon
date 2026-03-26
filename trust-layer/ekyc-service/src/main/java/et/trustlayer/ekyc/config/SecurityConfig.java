package et.trustlayer.ekyc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Simplified for service to service or direct API calls
            .authorizeHttpRequests(authz -> authz
                // For hackathon, allowing open access to verify from auth-server/app.
                // In production, this would validate an OAuth2 resource server JWT.
                .requestMatchers("/api/ekyc/**", "/actuator/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
