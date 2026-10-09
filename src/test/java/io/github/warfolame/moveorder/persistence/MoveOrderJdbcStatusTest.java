package io.github.warfolame.moveorder.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The status column mapping, without a database. */
class MoveOrderJdbcStatusTest {

    /** A new status that is not mapped both ways fails here before it reaches the table. */
    @ParameterizedTest
    @EnumSource(MoveOrderStatus.class)
    void everyStatusSurvivesTheColumn(MoveOrderStatus status) {
        assertEquals(status, MoveOrderJdbc.toStatus(MoveOrderJdbc.toColumn(status)));
    }

    @ParameterizedTest(name = "status \"{0}\" from the database is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {"LOST", "requested", "CANCELLED"})
    void unknownColumnTextThrows(String column) {
        assertThrows(IllegalStateException.class, () -> MoveOrderJdbc.toStatus(column));
    }
}
