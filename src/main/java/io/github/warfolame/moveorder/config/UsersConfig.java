package io.github.warfolame.moveorder.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Who may log in, and how a password is checked. Only {@code POST /api/v1/auth/login} uses this:
 * every other request is checked by its token, without a password and without a user lookup.
 */
@Configuration
public class UsersConfig {

    /** BCrypt by default; a stored hash carries its algorithm as a prefix: {@code {bcrypt}$2a$10$...}. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The two classroom users, only on the laptop. {@code User.roles("MANAGER")} stores
     * {@code ROLE_MANAGER}; the password from yaml is encoded here, so the user store
     * holds only BCrypt hashes.
     */
    @Bean
    @Profile("dev")
    public UserDetailsService devUsers(DevUsersProperties dev, PasswordEncoder encoder) {
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        for (DevUsersProperties.DevUser user : dev.users()) {
            users.createUser(User.withUsername(user.username())
                    .password(encoder.encode(user.password()))
                    .roles(user.role())
                    .build());
        }
        return users;
    }

    @Configuration
    @Profile("dev")
    @EnableConfigurationProperties(DevUsersProperties.class)
    static class DevUsersBinding {
    }

    /**
     * Finds the user in the {@link UserDetailsService} and compares the hash. An unknown name
     * and a wrong password both end as {@code BadCredentialsException}, so the caller cannot
     * tell which names exist. Outside dev there are no users yet (the user table is a later lab),
     * so every login there is a 401.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            ObjectProvider<UserDetailsService> users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                users.getIfAvailable(() -> UsersConfig::nobody));
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    private static User nobody(String username) {
        throw new UsernameNotFoundException("no users outside the dev profile");
    }
}
