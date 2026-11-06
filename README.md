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

The adapter `PaymentsClient` (`client`) already knows the provider's base URL and timeout from
yaml (Lab 4), but makes no call until Lab 6. In `DepositConfirmationTest` the port is a Mockito
mock: no test calls a real endpoint.

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
| `persistence` | `MoveOrderJdbc` (`@Repository`): `insert`, `count`, `findById`, `findByNumber` with `JdbcTemplate` and `?`; status ↔ text mapping; duplicate key → `DuplicateMoveOrder` |
| `config`      | `MoveOrderService` (`@Service`, `@Transactional` on `register` and `insertTwice`); DataSource from `application-dev.yaml` |

The repository maps `MoveOrderStatus` to the text column and back with an explicit `switch`.
Text that is not one of the four statuses throws `IllegalStateException`; it never becomes a new status.

## REST API

Another team never opens our code. They get a URL, a JSON body and a status code.

| Call                            | Meaning                                                      |
|---------------------------------|--------------------------------------------------------------|
| `POST /api/v1/move-orders`      | register one new move order; it starts in `REQUESTED`         |
| `GET /api/v1/move-orders/{id}`  | read it; `{id}` is the UUID from Lab 3, not `MO-2026-0042`    |

The version is in the path (`/api/v1`). No `PUT`, `PATCH`, `DELETE` or list in this lab.

**Request** (`Content-Type: application/json`): both fields required, title at most 120 characters.

```json
{"businessKey": "MO-2026-0042", "title": "2-room flat: Abay 10 -> Dostyk 5"}
```

**Response** (`201 Created`, `Location: .../api/v1/move-orders/{id}`, and the same body on `GET`):

```json
{"id": "346d2437-0f31-4a46-b821-a6c40d74881e", "businessKey": "MO-2026-0042",
 "status": "REQUESTED", "title": "2-room flat: Abay 10 -> Dostyk 5"}
```

`status` is the same token the `CHECK` constraint stores. The response is its own record
(`MoveOrderResponse`): the domain `MoveOrder` and the table never reach Jackson.

### Outcomes

| Situation                                                    | Status | `code`               |
|--------------------------------------------------------------|--------|----------------------|
| order registered                                             | 201    | (body is the order)  |
| `title` or `businessKey` blank, or `title` longer than 120   | 400    | `VALIDATION`         |
| path id is not a UUID, or the body is not JSON               | 400    | `VALIDATION`         |
| UUID parses and no order has it                              | 404    | `NOT_FOUND`          |
| business key already stored (the first order stays)          | 409    | `DUPLICATE`          |
| `Content-Type` is not `application/json`                     | 415    | `UNSUPPORTED`        |
| a path we do not serve / a method we do not allow            | 404 / 405 | `NOT_FOUND` / `METHOD_NOT_ALLOWED` |
| a bug nobody mapped                                          | 500    | `INTERNAL`           |

Every failure has the same body, and only these three fields: no stack trace, no exception class.

```json
{"code": "DUPLICATE", "message": "duplicate move order: MO-2026-0042", "correlationId": "demo-1"}
```

`correlationId` repeats the caller's `X-Correlation-Id` header; if the header is missing or blank,
a new UUID is minted. A 500 logs the exception with that id and says only `unexpected error`.

Who decides what:

- `MoveOrderController` (`handler`) picks 201 and builds `Location`. It calls the Lab 3
  `MoveOrderService.register` and the new `get`; it has no SQL and no business rules.
- `CreateMoveOrderRequest` carries `@NotBlank` / `@Size(max = 120)`. `@Valid` runs them before the
  controller method, so on a 400 the service is never called.
- `ApiErrorAdvice` (`@RestControllerAdvice`) is the one place that maps exceptions to 400 / 404 / 409 /
  415 / 500. The domain exceptions `MoveOrderNotFound` and `DuplicateMoveOrder` know nothing about HTTP.

### Real calls (`mvn spring-boot:run`)

```text
$ curl -i -X POST localhost:8080/api/v1/move-orders -H 'Content-Type: application/json' \
       -d '{"businessKey":"MO-2026-0042","title":"2-room flat: Abay 10 -> Dostyk 5"}'
HTTP/1.1 201
Location: http://localhost:8080/api/v1/move-orders/346d2437-0f31-4a46-b821-a6c40d74881e
{"id":"346d2437-...","businessKey":"MO-2026-0042","status":"REQUESTED","title":"2-room flat: Abay 10 -> Dostyk 5"}

$ curl -i -X POST ... -H 'X-Correlation-Id: demo-1' -d '{"businessKey":"MO-2026-0042","title":"Again"}'
HTTP/1.1 409
{"code":"DUPLICATE","message":"duplicate move order: MO-2026-0042","correlationId":"demo-1"}

$ curl -i -X POST ... -d '{"businessKey":"","title":"  "}'
HTTP/1.1 400
{"code":"VALIDATION","message":"businessKey must not be blank; title must not be blank","correlationId":"5a84762b-..."}

$ curl -i localhost:8080/api/v1/move-orders/MO-2026-0042
HTTP/1.1 400
{"code":"VALIDATION","message":"id must be a UUID","correlationId":"ab277773-..."}

$ curl -i localhost:8080/api/v1/move-orders/11111111-1111-1111-1111-111111111111
HTTP/1.1 404
{"code":"NOT_FOUND","message":"move order not found: 11111111-1111-1111-1111-111111111111","correlationId":"1c8121ab-..."}

$ psql -h localhost -U css -d css -c "SELECT count(*) FROM move_order WHERE business_key = 'MO-2026-0042'"
 count
-------
     1
```

### Contract

`/swagger-ui.html` shows the operations; `/v3/api-docs` is the OpenAPI spec (springdoc). It lists
`201`, `400`, `409` (and `415`) on create, `200`, `404` (and `400`) on get, both request fields as
required, and the response fields `id`, `businessKey`, `status`, `title`.

Compatibility: adding an optional field or clearer message text stays on `/api/v1`. Renaming `title`,
turning 201 into 200 or a status token into a number would be `/api/v2`, with v1 left alone.

### Tests

| Test                      | What it proves                                                                                |
|---------------------------|-----------------------------------------------------------------------------------------------|
| `MoveOrderControllerTest` | `@WebMvcTest` slice, service mocked, no database: 201 + `Location`, the five bodies (four 400s, title of 120 is 201), 404, 409, UUID in path, not JSON, 415, correlation id echoed and minted, 500 without internals |
| `MoveOrderApiTest`        | the whole app on PostgreSQL: `Location` can be read back; a second `POST` of the same key is 409 and the count stays **1**; a 400 stores nothing |

## Configuration, profiles, health

Nothing that changes between a laptop and a server is written in Java. A grep of `src/main/java`
for `jdbc:postgresql` or `http://` finds nothing.

| File                     | Holds                                                                                      |
|--------------------------|--------------------------------------------------------------------------------------------|
| `application.yaml`       | what every run shares: `spring.profiles.default: dev`, the error-leak settings, actuator exposure, `info`. No URL, no password |
| `application-dev.yaml`   | the laptop: PostgreSQL `localhost:5432/css`, user and password `css` (classroom default); runs `db/schema.sql` on start; payment provider `http://localhost:9090`, timeout `2s` |
| `application-prod.yaml`  | a server: `CSS_DB_URL`, `CSS_DB_USER`, `CSS_DB_PASSWORD`, `CSS_DOWNSTREAM_URL` from the environment. No default secret |

A plain `mvn spring-boot:run` uses `dev`. A server runs with `SPRING_PROFILES_ACTIVE=prod` and the
four variables. The committed files never activate `prod`.

There is no JPA and no migration tool in this project (Lab 3 is JDBC by design), so the shared yaml
has no `ddl-auto`, `open-in-view` or migration keys. The table is still our hand-written `schema.sql`;
`CREATE TABLE IF NOT EXISTS` lets the dev profile run it on every start.

### The payment provider's address

`app.downstream.base-url` and `app.downstream.timeout` are bound to the record `DownstreamProperties`
by `@ConfigurationProperties(prefix = "app.downstream")`. The timeout is a `Duration` (`2s`), not a
number of milliseconds. `PaymentsConfig` passes both into `PaymentsClient` (`client`), the adapter for
the Lab 3 port `DepositPayments`. It does not call the network: the real call and the timeout are Lab 6.

### Actuator

| Call                       | Result                                                 |
|----------------------------|--------------------------------------------------------|
| `GET /actuator/health`     | **200** `{"status":"UP"}`; no details, no datasource URL |
| `GET /actuator/info`       | **200** `{"app":{"name":"Move Order Tracker",...}}`     |
| `GET /actuator/env`, `/beans`, `/heapdump`, `POST /actuator/shutdown` | **404**, not exposed |

### Tests

| Test              | What it proves                                                                          |
|-------------------|-----------------------------------------------------------------------------------------|
| `DevProfileTest`  | with `dev`, the base URL is `http://localhost:9090` and the timeout 2 seconds, `PaymentsClient` received them, the database is localhost `css`; other yaml binds other values. No socket to port 9090 |
| `ProdProfileTest` | with `prod`, the datasource and the base URL come from `CSS_*` variables                 |
| `ActuatorTest`    | health is 200 and UP without details, info carries the product name, `env`, `beans`, `heapdump`, `configprops`, `metrics`, `shutdown` are 404 |

## Package diagram

```
  handler                      config                          persistence
  MoveOrderController   ---->  Application                     MoveOrderJdbc  @Repository
    POST/GET /api/v1/...       MoveOrderService  @Service  ->  (JdbcTemplate, ?)
  CreateMoveOrderRequest         @Transactional                     |
  MoveOrderResponse            MoveOrderRulesConfig  @Bean          |
  ErrorBody, ApiErrorAdvice    DownstreamProperties  (yaml)        |
        |                      PaymentsConfig  @Bean  --> client: PaymentsClient
        |                      injects MoveOrderRule          implements DepositPayments
        v                             v                             v
                                       domain
          MoveOrderId  MoveOrderNumber  MoveOrderStatus  MoveOrderPolicy  MoveOrder
          MoveOrderRule + TransitionRule, DepositRequiredRule (MoveOrderRuleChain)
          DepositPayments (port), DepositConfirmation, PaymentsUnavailable
          DuplicateMoveOrder, MoveOrderNotFound
                       (no Spring, no JDBC, no HTTP, no jakarta.validation)
```

Arrows point **inward**: the outer packages use `domain`, and `domain` uses nothing else.
`domain` has no `org.springframework`, `ResponseEntity`, `jakarta.validation` or `java.sql` import.
`dto` is empty for now.

## Run

The app and the build need PostgreSQL on `localhost:5432`, database `css`, user `css`, password `css`
(the classroom laptop default in `application-dev.yaml`, not a production secret). Either:

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
mvn spring-boot:run    # dev profile: creates the table if missing, serves on port 8080
```

| URL                                         | What                         |
|---------------------------------------------|------------------------------|
| http://localhost:8080/swagger-ui.html       | the API, clickable           |
| http://localhost:8080/v3/api-docs           | the OpenAPI spec             |
| http://localhost:8080/actuator/health       | `{"status":"UP"}`            |
| http://localhost:8080/api/v1/move-orders    | `POST` here (see REST API)   |

Boundary checks (each prints nothing):

```bash
grep -rE "java\.sql|org\.springframework|jakarta\.validation|ResponseEntity" src/main/java/io/github/warfolame/moveorder/domain
grep -rE "jdbc:postgresql|http://" src/main/java
grep -rniE "jakarta\.persistence|data-jpa" --include=*.java --include=pom.xml .
```
