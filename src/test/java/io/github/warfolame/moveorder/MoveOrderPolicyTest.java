package io.github.warfolame.moveorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MoveOrderPolicyTest {

    private final MoveOrderPolicy policy = new MoveOrderPolicy();

    /** The same four rows as the "Status table" in README.md. */
    @ParameterizedTest(name = "{0} -> {1}: allowed={2} ({3})")
    @CsvSource(delimiter = '|', textBlock = """
            REQUESTED | SCHEDULED   | yes | crew and date assigned
            SCHEDULED | IN_PROGRESS | yes | crew arrived, move started
            REQUESTED | COMPLETED   | no  | cannot close an order with no crew scheduled (skipped scheduling)
            COMPLETED | IN_PROGRESS | no  | cannot reopen a completed, paid order
            """)
    void moveFollowsStatusTable(MoveOrderStatus from, MoveOrderStatus to, String allowed, String reason) {
        switch (allowed) {
            case "yes" -> assertEquals(to, policy.move(from, to), reason);
            case "no" -> assertThrows(IllegalStateException.class, () -> policy.move(from, to), reason);
            default -> fail("Allowed must be 'yes' or 'no', but was: " + allowed);
        }
    }

    @ParameterizedTest(name = "id \"{0}\" is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void idRejectsNullAndBlank(String value) {
        assertThrows(IllegalArgumentException.class, () -> new MoveOrderId(value));
    }

    @Test
    void idKeepsValidValue() {
        assertEquals("MO-2026-0042", new MoveOrderId("MO-2026-0042").value());
    }
}
