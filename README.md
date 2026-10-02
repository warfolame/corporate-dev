## Product

We build a move order tracker for a moving company. People track move orders.

## Core item

A **move order** is one customer's move from address A to address B.
Every order (`MoveOrder`) has an id (`MoveOrderId`), a status (`MoveOrderStatus`)
and a flag that says whether the customer has paid the deposit:

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

## Rules

Every status change goes through one `MoveOrderRule`. Two rules implement it:

| Rule                  | What it checks                                                                       |
|-----------------------|--------------------------------------------------------------------------------------|
| `TransitionRule`      | the status table above (`MoveOrderPolicy`)                                           |
| `DepositRequiredRule` | stop-factor: no `SCHEDULED` while the deposit is unpaid — no crew is booked for free |

The table allows `REQUESTED → SCHEDULED`, but the stop-factor still blocks it until the deposit is paid.
`MoveOrderRuleChain` checks both in order; `MoveOrderRulesConfig` builds the chain as a `@Bean`,
and `MoveOrderService` (`@Service`) gets it injected as a `MoveOrderRule`.

## Package diagram

```
  dto            client          handler          config
  (JSON later)   (HTTP later)    (HTTP week 9)    Application
                                                  MoveOrderService      @Service
                                                  MoveOrderRulesConfig  @Bean
       \              \              /                  |
        \              \            /           injects MoveOrderRule
         v              v          v                    v
                             domain
          MoveOrderId  MoveOrderStatus  MoveOrderPolicy  MoveOrder
          MoveOrderRule + TransitionRule, DepositRequiredRule
                          (MoveOrderRuleChain)
                             (no Spring)
```

Arrows point **inward**: the outer packages use `domain`, and `domain` uses nothing else.
`domain` has no `org.springframework` import. `dto`, `client` and `handler` are empty for now.

## Run

```bash
mvn -q verify          # compile + all tests
mvn spring-boot:run    # starts the app; it walks one order through the rules and logs each step
```
