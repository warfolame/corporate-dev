## Product

We build a move order tracker for a moving company. People track move orders.

## Core item

A **move order** is one customer's move from address A to address B.
Every order has an id (`MoveOrderId`) and a status (`MoveOrderStatus`):

`REQUESTED` → `SCHEDULED` → `IN_PROGRESS` → `COMPLETED`

`MoveOrderPolicy.move(from, to)` returns the new status if the change is allowed
and throws `IllegalStateException` if it is forbidden.

## Status table

| From      | To          | Allowed | Reason                                                            |
|-----------|-------------|---------|-------------------------------------------------------------------|
| REQUESTED | SCHEDULED   | yes     | crew and date assigned                                            |
| SCHEDULED | IN_PROGRESS | yes     | crew arrived, move started                                        |
| REQUESTED | COMPLETED   | no      | cannot close an order with no crew scheduled (skipped scheduling) |
| COMPLETED | IN_PROGRESS | no      | cannot reopen a completed, paid order                             |

## Forbidden — why

- **REQUESTED → COMPLETED.** A requested order has no crew and no date, so nobody has done the move yet.
  If it could be closed right away, the company would mark as done (and bill for) a move that never happened,
  and the customer would be left without movers. An order must be scheduled and actually started first.
- **COMPLETED → IN_PROGRESS.** A completed order is already paid, and the crew has already been paid for it.
  Reopening it would change closed financial records and the crew's work history.
  Any extra work (a forgotten box, a damage claim) is a new order with its own price.
