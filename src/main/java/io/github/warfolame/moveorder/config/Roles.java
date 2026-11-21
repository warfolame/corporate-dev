package io.github.warfolame.moveorder.config;

import java.util.Set;

/**
 * The two roles of the move order tracker, as the README rules table names them.
 * The filter chain ({@link SecurityConfig}) and {@code @PreAuthorize} on
 * {@link MoveOrderService#complete} both read the names from here, so the URL rule and the
 * method rule cannot drift apart. Spring stores them as {@code ROLE_DISPATCHER} / {@code ROLE_MANAGER};
 * the token's {@code roles} claim carries them without the prefix.
 */
public final class Roles {

    /** Junior role: registers move orders and reads them. Cannot complete one. */
    public static final String DISPATCHER = "DISPATCHER";

    /** Senior role: everything a dispatcher does, and completes a move (closes and bills it). */
    public static final String MANAGER = "MANAGER";

    public static final Set<String> ALL = Set.of(DISPATCHER, MANAGER);

    private Roles() {
    }
}
