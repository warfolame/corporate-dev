package io.github.warfolame.moveorder.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.warfolame.moveorder.domain.DuplicateMoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.github.warfolame.moveorder.persistence.MoveOrderJdbc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test against the real PostgreSQL from application-dev.yaml.
 * The test opens no transaction of its own: every count is read after the
 * service's transaction has committed or rolled back, the way production sees it.
 */
@SpringBootTest
class MoveOrderStorageTest {

    @Autowired
    private MoveOrderService service;

    @Autowired
    private MoveOrderJdbc orders;

    @Autowired
    private JdbcTemplate jdbc;

    private final MoveOrderNumber number = new MoveOrderNumber("MO-2026-0042");

    /** Starts every test from an empty table built by schema.sql itself. */
    @BeforeEach
    void cleanDatabase() throws IOException {
        jdbc.execute("DROP TABLE IF EXISTS move_order");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/schema.sql")));
    }

    @Test
    void secondStatementRollsBack() {
        assertThrows(DuplicateMoveOrder.class, () -> service.insertTwice(number));

        assertEquals(0, orders.count(number));
    }

    @Test
    void secondRequestKeepsTheFirst() {
        service.register(MoveOrderId.newId(), number, "2-room flat: Abay 10 -> Dostyk 5");

        assertThrows(DuplicateMoveOrder.class,
                () -> service.register(MoveOrderId.newId(), number, "Same flat, sent again"));

        assertEquals(1, orders.count(number));
    }

    @Test
    void registeredOrderReadsBackUnchanged() {
        MoveOrder registered = service.register(MoveOrderId.newId(), number, "2-room flat: Abay 10 -> Dostyk 5");

        assertEquals(registered, orders.findByNumber(number).orElseThrow());
    }

    @Test
    void unknownNumberIsNotFound() {
        assertTrue(orders.findByNumber(new MoveOrderNumber("MO-2026-9999")).isEmpty());
    }
}
