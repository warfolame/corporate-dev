package io.github.warfolame.moveorder.config;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import io.github.warfolame.moveorder.handler.SecurityErrors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who is calling, and may they. Every request passes these filters before any controller:
 * a missing or bad token is a 401, a role the rule does not allow is a 403, and the controller,
 * the service and the database are never reached.
 *
 * <p>The rules are the README rules table, top to bottom; the first match wins. The locked verb
 * ({@code complete}) is narrower than {@code /api/v1/**}, so it comes first: the other way round,
 * a dispatcher would match the wide rule and complete orders.
 *
 * <p>{@code @EnableMethodSecurity} turns on the second lock, {@code @PreAuthorize} on
 * {@link MoveOrderService#complete}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** The auth page: exact files only. {@code /} here is the page, not a prefix, so {@code /api} stays locked. */
    static final String[] PAGE = {"/", "/index.html", "/app.css", "/app.js"};

    static final String[] SPRINGDOC = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    @Bean
    public SecurityErrors securityErrors(JsonMapper json) {
        return new SecurityErrors(json);
    }

    @Bean
    public SecurityFilterChain api(HttpSecurity http, SecurityErrors errors) throws Exception {
        return http
                .authorizeHttpRequests(rules -> rules
                        // 1. log in: you come here to get a token
                        .requestMatchers(POST, "/api/v1/auth/login").permitAll()
                        // 2. health (and the product name from Lab 4)
                        .requestMatchers(GET, "/actuator/health", "/actuator/info").permitAll()
                        // 3. the static auth page, its css and js
                        .requestMatchers(GET, PAGE).permitAll()
                        // 4. the contract
                        .requestMatchers(GET, SPRINGDOC).permitAll()
                        // 5. the locked verb: the senior role only. Narrow rule before the wide one
                        .requestMatchers(POST, "/api/v1/move-orders/*/complete").hasRole(Roles.MANAGER)
                        // 6. create and read move orders: either role
                        .requestMatchers("/api/v1/**").hasAnyRole(Roles.DISPATCHER, Roles.MANAGER)
                        // who am I: either role
                        .requestMatchers(GET, "/api/me").hasAnyRole(Roles.DISPATCHER, Roles.MANAGER)
                        // 7. everything else, including actuator env/beans/heapdump: nobody
                        .anyRequest().denyAll())
                // the credential is Authorization: Bearer <JWT>, checked by JwtConfig's JwtDecoder
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromToken()))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                // no cookie, no session: CSRF protects cookies, and we have none
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // not Spring's "Please sign in" page, not Basic, no /logout endpoint: log out = drop the token
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Our roles are in the {@code roles} claim, not in Spring's default {@code scope}, and
     * {@code hasRole("MANAGER")} looks for {@code ROLE_MANAGER}. So: read {@code roles}, add {@code ROLE_}.
     * Without the prefix every manager call would be a 403. The user's name is {@code sub}.
     */
    static JwtAuthenticationConverter rolesFromToken() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
