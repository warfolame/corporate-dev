package io.github.warfolame.moveorder.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The classroom users from {@code app.dev.users} in {@code application-dev.yaml}.
 * Only the dev profile binds this; production has no such users.
 *
 * @param users two users, one per role
 */
@ConfigurationProperties(prefix = "app.dev")
public record DevUsersProperties(List<DevUser> users) {

    public DevUsersProperties {
        if (users == null || users.isEmpty()) {
            throw new IllegalArgumentException("app.dev.users must list the dev users");
        }
        users = List.copyOf(users);
    }

    /**
     * @param username the login name, also {@code sub} in the token
     * @param password the classroom password; {@link UsersConfig} keeps only its BCrypt hash
     * @param role     {@code DISPATCHER} or {@code MANAGER}, without {@code ROLE_}
     */
    public record DevUser(String username, String password, String role) {

        public DevUser {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("app.dev.users[].username must be set");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("app.dev.users[].password must be set for " + username);
            }
            if (!Roles.ALL.contains(role)) {
                throw new IllegalArgumentException(
                        "app.dev.users[].role of " + username + " must be one of " + Roles.ALL + ", was " + role);
            }
        }

        /** The password stays out of logs. */
        @Override
        public String toString() {
            return "DevUser[username=" + username + ", role=" + role + "]";
        }
    }
}
