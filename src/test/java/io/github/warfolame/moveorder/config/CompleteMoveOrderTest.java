package io.github.warfolame.moveorder.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNotFound;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import io.github.warfolame.moveorder.domain.TransitionNotAllowed;
import io.github.warfolame.moveorder.persistence.MoveOrderJdbc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * The second lock and the status change, without HTTP: {@code @PreAuthorize} on
 * {@link MoveOrderService#complete} against the real PostgreSQL. Even if a URL rule were
 * forgotten, a dispatcher could not complete an order through the service.
 */
@SpringBootTest
class CompleteMoveOrderTest {

    @Autowired
    private MoveOrderService service;

    @Autowired
    private MoveOrderJdbc orders;

    @Autowired
    private JdbcTemplate jdbc;

    private final MoveOrder inProgress = MoveOrder.requested(
                    MoveOrderId.newId(), new MoveOrderNumber("MO-2026-0042"), "2-room flat: Abay 10 -> Dostyk 5")
            .payDeposit()
            .withStatus(MoveOrderStatus.IN_PROGRESS);

    @BeforeEach
    void cleanDatabase() throws IOException {
        jdbc.execute("DROP TABLE IF EXISTS move_order");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/schema.sql")));
        orders.insert(inProgress);
    }

    private MoveOrderStatus stored(MoveOrder order) {
        return orders.findById(order.id()).orElseThrow().status();
    }

    @Test
    void nobodySignedInCannotComplete() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> service.complete(inProgress.id(), "nobody"));

        assertEquals(MoveOrderStatus.IN_PROGRESS, stored(inProgress));
    }

    @Test
    @WithMockUser(username = "aliya", roles = Roles.DISPATCHER)
    void dispatcherIsStoppedBeforeTheMethodRuns() {
        assertThrows(AccessDeniedException.class, () -> service.complete(inProgress.id(), "aliya"));

        assertEquals(MoveOrderStatus.IN_PROGRESS, stored(inProgress));
    }

    @Test
    @WithMockUser(username = "nurlan", roles = Roles.MANAGER)
    void managerCompletesAndTheRowIsWritten() {
        MoveOrder completed = service.complete(inProgress.id(), "nurlan");

        assertEquals(MoveOrderStatus.COMPLETED, completed.status());
        assertEquals(MoveOrderStatus.COMPLETED, stored(inProgress));
    }

    @Test
    @WithMockUser(username = "nurlan", roles = Roles.MANAGER)
    void thePolicyStillDecidesForAManager() {
        MoveOrder requested = MoveOrder.requested(MoveOrderId.newId(), new MoveOrderNumber("MO-2026-0043"), "Office");
        orders.insert(requested);

        assertThrows(TransitionNotAllowed.class, () -> service.complete(requested.id(), "nurlan"));

        assertEquals(MoveOrderStatus.REQUESTED, stored(requested));
    }

    @Test
    @WithMockUser(username = "nurlan", roles = Roles.MANAGER)
    void unknownOrderIsNotFound() {
        assertThrows(MoveOrderNotFound.class, () -> service.complete(MoveOrderId.newId(), "nurlan"));
    }

    /** dev-data.sql: one IN_PROGRESS demo order for the auth page, reset on every run of the script. */
    @Test
    @WithMockUser(username = "nurlan", roles = Roles.MANAGER)
    void theDevDemoOrderIsBackInProgressAfterEveryStart() throws IOException {
        String seed = Files.readString(Path.of("src/main/resources/db/dev-data.sql"));
        MoveOrderId demo = new MoveOrderId(java.util.UUID.fromString("5eed0001-2026-4000-8000-000000000001"));

        jdbc.execute(seed);
        service.complete(demo, "nurlan");
        jdbc.execute(seed);

        MoveOrder reset = orders.findById(demo).orElseThrow();
        assertEquals(MoveOrderStatus.IN_PROGRESS, reset.status());
        assertEquals("MO-2026-0001", reset.number().value());
        assertEquals(1, orders.count(new MoveOrderNumber("MO-2026-0001")));
    }
}
