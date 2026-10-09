## Product

We build a move order tracker for a moving company. People track move orders.

## Core item

A **move order** is one customer's move from address A to address B.
Every order (`MoveOrder`) has two identifiers, a title, a status (`MoveOrderStatus`)
and a flag that says whether the customer has paid the deposit:

| Field    | Type              | Example                            | Who creates it        |
|----------|-------------------|------------------------------------|-----------------------|
| `id`     | `MoveOrderId`     | a UUID                             | `MoveOrderId.newId()` |
| `number` | `MoveOrderNumber` | `MO-2026-0042`                     | the sales process     |
| `title`  | `String`          | `2-room flat: Abay 10 -> Dostyk 5` | the sales manager     |

`id` is the surrogate key: it never changes and nobody types it. `number` is the business key:
it is in the customer's email, and one number belongs to at most one order.
Null or blank values are rejected in the constructors.

Statuses:

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

## Outbound port

The deposit is paid to an outside payment provider. The domain owns the port
`DepositPayments` (`isDepositPaid(number)`); `DepositConfirmation` asks it and marks the
order paid. If the provider fails (timeout, 503), the caller gets our `PaymentsUnavailable`
with the order number, not the provider's stack trace.

The HTTP adapter for the provider comes later in `client`. Until then the port is used only
in `DepositConfirmationTest`, where it is a Mockito mock: no test calls a real endpoint.

## Storage

Move orders live in PostgreSQL, in one table written by hand in
[`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql). Nothing generates it:
no ORM, no migration tool. The application talks to it over plain JDBC.

```sql
CREATE TABLE move_order (
  id            uuid PRIMARY KEY,
  business_key  text NOT NULL UNIQUE,
  status        text NOT NULL,
  title         text NOT NULL,
  deposit_paid  boolean NOT NULL DEFAULT false,
  created_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT move_order_status_known
    CHECK (status IN ('REQUESTED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED'))
);
```

| Column         | Domain                   | Why it is here                                                  |
|----------------|--------------------------|-----------------------------------------------------------------|
| `id`           | `MoveOrderId` (UUID)     | surrogate key; future foreign keys point here                   |
| `business_key` | `MoveOrderNumber`        | `MO-2026-0042`; `UNIQUE`, so a retry cannot store it twice      |
| `status`       | `MoveOrderStatus`        | text, but only the four statuses of the README (`CHECK`)        |
| `title`        | `MoveOrder.title`        | the human sentence about the move                               |
| `deposit_paid` | `MoveOrder.depositPaid`  | a fact about money for `DepositRequiredRule`, not a status      |
| `created_at`   | —                        | the database clock, set even if Java forgets                    |

`UNIQUE` alone lets many `NULL`s in; `NOT NULL` closes that. Blank strings are rejected in Java
(`MoveOrderNumber`, `MoveOrder`). The number is not the primary key: if its format changes,
no foreign key has to move.

Java keeps the transitions (`MoveOrderPolicy`, rules). SQL keeps the row honest: a known status,
one order per number, no missing title or number.

### Two transactions, two counts

| Test (`MoveOrderStorageTest`) | What happens                                                                         | Count |
|-------------------------------|--------------------------------------------------------------------------------------|-------|
| `secondStatementRollsBack`    | `insertTwice`: **one** transaction, two `INSERT`s of `MO-2026-0042`, the second fails | **0** |
| `secondRequestKeepsTheFirst`  | two `register` calls, one transaction **each**; the first commits, the second fails  | **1** |

A duplicate number arrives from the driver as SQLState `23505`, which Spring turns into
`DuplicateKeyException` (a `DataIntegrityViolationException`). `MoveOrderJdbc` catches it and
**rethrows** our unchecked `DuplicateMoveOrder`. It never returns normally after a duplicate:
that would commit the first `INSERT` of `insertTwice`.

`@Transactional` is on `MoveOrderService`, not on a policy and not on the test. The test reads
each count outside the transaction under test, so it sees only what was really committed.
Before every test it drops `move_order` and runs `schema.sql`, so it starts from an empty table.

### Rollback, proved in psql first

```text
css=> BEGIN;
css=*> INSERT INTO move_order (id, business_key, status, title)
         VALUES ('11111111-1111-1111-1111-111111111111',
                 'MO-2026-0042', 'REQUESTED', '2-room flat: Abay 10 -> Dostyk 5');
INSERT 0 1
css=*> INSERT INTO move_order (id, business_key, status, title)
         VALUES ('22222222-2222-2222-2222-222222222222',
                 'MO-2026-0042', 'REQUESTED', 'Same flat, sent again');
ERROR:  duplicate key value violates unique constraint "move_order_business_key_key"
DETAIL:  Key (business_key)=(MO-2026-0042) already exists.
css=!> ROLLBACK;
css=> SELECT count(*) FROM move_order WHERE business_key = 'MO-2026-0042';
 count
-------
     0

css=> INSERT INTO move_order (id, business_key, status, title)
      VALUES ('33333333-3333-3333-3333-333333333333', 'MO-2026-0043', 'LOST', 'Office move');
ERROR:  new row for relation "move_order" violates check constraint "move_order_status_known"
```

### Where the code lives

| Package       | What                                                                                            |
|---------------|-------------------------------------------------------------------------------------------------|
| `domain`      | `MoveOrderId`, `MoveOrderNumber`, `MoveOrderStatus`, `MoveOrderPolicy`, rules, `DuplicateMoveOrder`; no JDBC, no Spring |
| `persistence` | `MoveOrderJdbc` (`@Repository`): `insert`, `count`, `find` with `JdbcTemplate` and `?`; status ↔ text mapping; duplicate key → `DuplicateMoveOrder` |
| `config`      | `MoveOrderService` (`@Service`, `@Transactional` on `register` and `insertTwice`); DataSource from `application.yml` |

The repository maps `MoveOrderStatus` to the text column and back with an explicit `switch`.
Text that is not one of the four statuses throws `IllegalStateException`; it never becomes a new status.

## Package diagram

```
  dto            client          handler        config                          persistence
  (JSON later)   (HTTP later)    (HTTP week 9)  Application                     MoveOrderJdbc  @Repository
                                                MoveOrderService  @Service  ->  (JdbcTemplate, ?)
                                                  @Transactional                     |
                                                MoveOrderRulesConfig  @Bean          |
       \              \              /               |                             |
        \              \            /        injects MoveOrderRule                 |
         v              v          v                 v                             v
                                       domain
          MoveOrderId  MoveOrderNumber  MoveOrderStatus  MoveOrderPolicy  MoveOrder
          MoveOrderRule + TransitionRule, DepositRequiredRule (MoveOrderRuleChain)
          DepositPayments (port), DepositConfirmation, PaymentsUnavailable, DuplicateMoveOrder
                                  (no Spring, no JDBC)
```

Arrows point **inward**: the outer packages use `domain`, and `domain` uses nothing else.
`domain` has no `org.springframework` and no `java.sql` import. `dto`, `client` and `handler`
are empty for now.

## Run

The build needs PostgreSQL on `localhost:5432`, database `css`, user `css`, password `css`
(a classroom laptop default from `application.yml`, not a production secret). Either:

```bash
docker compose up -d   # compose.yaml runs only PostgreSQL 16, not the application
```

or, with a local PostgreSQL, once per laptop as the `postgres` superuser:

```sql
CREATE USER css WITH PASSWORD 'css';
CREATE DATABASE css OWNER css;
```

Then:

```bash
mvn -q verify          # compile + all tests; the storage tests drop and re-create move_order
mvn spring-boot:run    # starts the app; it walks one order through the rules and logs each step

psql -h localhost -U css -d css -f src/main/resources/db/schema.sql   # the table, by hand
```

Boundary checks (both print nothing):

```bash
grep -rE "java\.sql|org\.springframework" src/main/java/io/github/warfolame/moveorder/domain
grep -rniE "jakarta\.persistence|data-jpa" --include=*.java --include=pom.xml .
```
