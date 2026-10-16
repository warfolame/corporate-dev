package io.github.warfolame.moveorder.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The payment provider is a mocked port: no test here leaves the laptop. */
class DepositConfirmationTest {

    private final DepositPayments payments = mock(DepositPayments.class);
    private final DepositConfirmation confirmation = new DepositConfirmation(payments);

    private final MoveOrderNumber number = new MoveOrderNumber("MO-2026-0042");
    private final MoveOrder unpaid = MoveOrder.requested(
            MoveOrderId.newId(), number, "2-room flat: Abay 10 -> Dostyk 5");

    @Test
    void paidDepositIsRecordedOnTheOrder() {
        when(payments.isDepositPaid(number)).thenReturn(true);

        MoveOrder confirmed = confirmation.confirm(unpaid);

        assertTrue(confirmed.depositPaid());
    }

    @Test
    void unpaidDepositLeavesTheOrderUnpaid() {
        when(payments.isDepositPaid(number)).thenReturn(false);

        MoveOrder confirmed = confirmation.confirm(unpaid);

        assertFalse(confirmed.depositPaid());
    }

    @Test
    void providerIsAskedOnceAboutThisOrderNumber() {
        when(payments.isDepositPaid(number)).thenReturn(true);

        confirmation.confirm(unpaid);

        ArgumentCaptor<MoveOrderNumber> asked = ArgumentCaptor.forClass(MoveOrderNumber.class);
        verify(payments, times(1)).isDepositPaid(asked.capture());
        assertEquals(number, asked.getValue());
        verifyNoMoreInteractions(payments);
    }

    @Test
    void providerFailureBecomesPaymentsUnavailable() {
        when(payments.isDepositPaid(number)).thenThrow(new RuntimeException("503 Service Unavailable"));

        PaymentsUnavailable error = assertThrows(PaymentsUnavailable.class,
                () -> confirmation.confirm(unpaid));

        assertTrue(error.getMessage().contains("MO-2026-0042"));
    }
}
