package io.github.warfolame.moveorder.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Invariants of the order's identity: null and blank never get in. */
class MoveOrderTest {

    private final MoveOrderNumber number = new MoveOrderNumber("MO-2026-0042");

    @Test
    void idRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new MoveOrderId(null));
    }

    @Test
    void newIdIsDifferentEveryTime() {
        assertNotEquals(MoveOrderId.newId(), MoveOrderId.newId());
    }

    @ParameterizedTest(name = "number \"{0}\" is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void numberRejectsNullAndBlank(String value) {
        assertThrows(IllegalArgumentException.class, () -> new MoveOrderNumber(value));
    }

    @Test
    void numberKeepsValidValue() {
        assertEquals("MO-2026-0042", number.value());
    }

    @ParameterizedTest(name = "title \"{0}\" is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void titleRejectsNullAndBlank(String title) {
        assertThrows(IllegalArgumentException.class,
                () -> MoveOrder.requested(MoveOrderId.newId(), number, title));
    }
}
