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

| Call                                    | Meaning                                                      |
|-----------------------------------------|--------------------------------------------------------------|
| `POST /api/v1/move-orders`              | register one new move order; it starts in `REQUESTED`         |
| `GET /api/v1/move-orders/{id}`          | read it; `{id}` is the UUID from Lab 3, not `MO-2026-0042`    |
| `POST /api/v1/move-orders/{id}/complete`| Lab 5: the locked action, `IN_PROGRESS -> COMPLETED`, `MANAGER` only |
| `POST /api/v1/auth/login`, `GET /api/me`| Lab 5: get a token; who am I                                  |

The version is in the path (`/api/v1`). No `PUT`, `PATCH`, `DELETE` or list.
Since Lab 5 every call except the login needs `Authorization: Bearer <token>`:
see [Who may do what](#who-may-do-what-lab-5).

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
| no token, or a bad / expired / foreign token (Lab 5)         | 401    | `UNAUTHENTICATED`    |
| a valid token whose role may not do this (Lab 5)             | 403    | `FORBIDDEN`          |
| UUID parses and no order has it                              | 404    | `NOT_FOUND`          |
| business key already stored (the first order stays)          | 409    | `DUPLICATE`          |
| the status table refuses the change, e.g. completing a `REQUESTED` order (Lab 5) | 409 | `INVALID_TRANSITION` |
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

## Who may do what (Lab 5)

Until Lab 4 anyone on the network could create and read any move order, and nothing could say who
did it. Now a caller logs in once with a password, gets a signed JWT for 15 minutes, and shows it as
`Authorization: Bearer <token>` on every call. **Authentication** ("who are you?") fails as **401**.
**Authorization** ("may you do this?") fails as **403**.

### Roles and the locked action

| Role         | Who in the company                     | May do                                                      |
|--------------|----------------------------------------|-------------------------------------------------------------|
| `DISPATCHER` | junior: takes calls, books the orders  | register move orders, read them                             |
| `MANAGER`    | senior: signs off the money            | everything a dispatcher does, and **complete** a move       |

The locked action is the most expensive move of Lab 1: `IN_PROGRESS -> COMPLETED`. A completed
order is closed and billed, the crew is paid, and Lab 1 forbids reopening it. So only a manager may do it.

### Rules table

This is the filter chain, top to bottom; the first matching row wins. The tests lock every row.

| Action                                              | `DISPATCHER` | `MANAGER` | No token |
|-----------------------------------------------------|--------------|-----------|----------|
| `POST /api/v1/auth/login`                           | anyone       | anyone    | allowed  |
| `GET /actuator/health` (and `/actuator/info`, the product name from Lab 4) | anyone | anyone | allowed |
| the auth page: `/`, `/index.html`, `/app.css`, `/app.js` | anyone  | anyone    | allowed  |
| springdoc: `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | anyone | anyone | allowed |
| **Complete a move order**: `POST /api/v1/move-orders/{id}/complete` | **403** | yes | 401 |
| Create and read move orders: `/api/v1/**`           | yes          | yes       | 401      |
| `GET /api/me`                                       | yes          | yes       | 401      |
| Anything else (`/actuator/env`, `/heapdump`, `/login`, ...) | 403  | 403       | 401      |

The complete row is above `/api/v1/**` on purpose: the other way round, a dispatcher would match
the wide rule first and complete orders. The last row is `denyAll()`, not `authenticated()`:
an endpoint someone adds next month without a rule is closed, not open to every user.

### Dev users

`@Profile("dev")`, an `InMemoryUserDetailsManager` built in `UsersConfig` from `app.dev.users` in
`application-dev.yaml`. `User.roles(...)` stores `ROLE_DISPATCHER` / `ROLE_MANAGER`.

| Username | Password (classroom, dev only) | Role         |
|----------|--------------------------------|--------------|
| `aliya`  | `dispatcher-dev`               | `DISPATCHER` |
| `nurlan` | `manager-dev`                  | `MANAGER`    |

The user store holds only BCrypt hashes (`{bcrypt}$2a$10$...`, from
`PasswordEncoderFactories.createDelegatingPasswordEncoder()`); there is no `{noop}`. Production has
no users at all: no `UserDetailsService` bean, so every login there is a 401. No user table and no
migration in this lab.

### The token

`POST /api/v1/auth/login` with `{"username": "...", "password": "..."}` (both `@NotBlank`).
`AuthenticationManager` (a `DaoAuthenticationProvider`) checks the name and the hash; `TokenService`
signs the token with `JwtEncoder`.

| Login                       | Status | Body                                            |
|-----------------------------|--------|-------------------------------------------------|
| known user, right password  | 200    | `{"accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900}` |
| unknown user                | 401    | `UNAUTHENTICATED`, `bad username or password`   |
| known user, wrong password  | 401    | the same message                                |
| blank username or password  | 400    | `VALIDATION`, the field names                   |

One message for both 401 rows: a different text for "no such user" would tell an attacker which
names exist. The password is not in the response, not in the token, and not in a log line
(`LoginRequest.toString()` hides it; `AuthFlowTest` checks the captured log).

A token is `header.payload.signature`. Header and payload are only base64url, so anyone can read them:

```text
header:  {"alg":"HS256"}
payload: {"iss":"move-orders","sub":"aliya","exp":1795262938,"iat":1795262038,"roles":["DISPATCHER"]}
```

| Claim   | Value          | Meaning                                                              |
|---------|----------------|----------------------------------------------------------------------|
| `alg`   | `HS256`        | signed with our secret, HMAC-SHA256                                  |
| `iss`   | `move-orders`  | our product; the decoder rejects any other issuer (`helpdesk` is a 401 here) |
| `sub`   | `aliya`        | whose token it is; `Authentication.getName()`                        |
| `roles` | `["DISPATCHER"]` | role names **without** `ROLE_`; the converter adds the prefix      |
| `iat` / `exp` | 15 minutes apart | a stolen token dies quickly; then the client logs in again    |

No password, no phone, no IIN in the payload. Log out = the client drops the token; it cannot be
cancelled before `exp`, which is why it lives 15 minutes and not a day.

### The secret

| File                    | `app.jwt.secret`                                                     |
|-------------------------|----------------------------------------------------------------------|
| `application.yaml`      | none                                                                 |
| `application-dev.yaml`  | a classroom value of 52 characters, not a production secret         |
| `application-prod.yaml` | `${CSS_JWT_SECRET}`, no default                                      |

`JwtProperties` refuses a secret shorter than 32 bytes (256 bits, what HS256 needs), so `secret`
stops the application at startup, and so does a missing `CSS_JWT_SECRET`. A grep of `src/main/java`
for the dev secret finds nothing (`JwtSecretTest` does that grep). Whoever knows the secret can sign
a token for any user with any role, so it never goes to git for prod.

`JwtConfig` turns it into one `SecretKey` and two beans: `JwtEncoder` signs at login, `JwtDecoder`
checks every other request: HS256 signature, `exp` present and not passed, `iss` is `move-orders`.

### The filter chain

`SecurityConfig`: the rules table above, plus

- `oauth2ResourceServer().jwt()`: the credential is `Authorization: Bearer`. Not a cookie, not Basic.
- `JwtAuthenticationConverter`: roles are in the `roles` claim and need the `ROLE_` prefix
  (`hasRole("MANAGER")` looks for `ROLE_MANAGER`; without the prefix every manager would get 403).
- CSRF off (there is no cookie to protect), session `STATELESS`, `formLogin`, `httpBasic` and
  `logout` disabled. Spring's "Please sign in" page does not exist here.
- `SecurityErrors` is both the authentication entry point and the access-denied handler. 401 and
  403 happen in the filters, before any controller, so `@RestControllerAdvice` never sees them; this
  class writes our Lab 4 error record instead of an empty body.

| Check, in order                         | Fails as                                       |
|-----------------------------------------|------------------------------------------------|
| no `Authorization: Bearer`              | 401 `UNAUTHENTICATED` `valid token required`   |
| not three base64url pieces (`Bearer hello`) | 401                                        |
| signature is not our secret (an edited role, another secret) | 401                       |
| expired, or `iss` is not `move-orders`  | 401                                            |
| role is not allowed                     | 403 `FORBIDDEN` `not allowed for your role`    |

Every body is still `code`, `message`, `correlationId`. `X-Correlation-Id` is read as in Lab 4,
echoed, and minted when missing (`CorrelationId`, shared by the advice and the filters).

### The locked action

`POST /api/v1/move-orders/{id}/complete`. The path id is the UUID. There is no request body: the
controller takes `Authentication` and passes `getName()` into `MoveOrderService.complete(id, actor)`.
An `actor` or `role` field in JSON is ignored; the caller does not get to type the role.

The service method carries `@PreAuthorize("hasRole('MANAGER')")` (`@EnableMethodSecurity` on
`SecurityConfig`): the second lock, with the same role as the URL rule (both read `Roles.MANAGER`).
Inside one transaction it reads the row `FOR UPDATE`, asks the Lab 2 rules, writes the new status and
logs who did it. The domain policy stays plain Java: `TransitionNotAllowed` is a plain
`IllegalStateException`, with no `org.springframework` import.

| Call                                       | What happens                                                         |
|--------------------------------------------|----------------------------------------------------------------------|
| `DISPATCHER`                               | 403. The service method is not called. The row is unchanged.         |
| `MANAGER`, order is `IN_PROGRESS`          | 200, `"status": "COMPLETED"`. Log: `... IN_PROGRESS -> COMPLETED by nurlan`, no token in it. |
| `MANAGER`, order is `REQUESTED` or already `COMPLETED` | 409 `INVALID_TRANSITION`. The row does not change.       |
| `MANAGER`, no such id                      | 404 `NOT_FOUND`                                                      |
| no token                                   | 401. The service method is not called.                               |

A new order is `REQUESTED`, and Lab 1 only lets `IN_PROGRESS` be completed. So the dev profile
also runs [`db/dev-data.sql`](src/main/resources/db/dev-data.sql): one demo order `MO-2026-0001`
(id `5eed0001-2026-4000-8000-000000000001`), put back to `IN_PROGRESS` on every start. The schema
is unchanged; prod does not run this file.

### `GET /api/me`

The `Authentication` the filter put into the security context for this request, nothing from a body:

```json
{"username": "aliya", "authorities": ["ROLE_DISPATCHER"]}
```

### The auth page

`http://localhost:8080/` is our own page, served by the same Spring Boot process from
[`src/main/resources/static/`](src/main/resources/static/) (`index.html`, `app.css`, `app.js`;
no framework, no second dev server, so no CORS bean is needed).

- Before login: the product name, the two roles, and the two dev users (marked dev-only).
- Login form (`type="password"`): `POST /api/v1/auth/login` with `Content-Type: application/json`.
  A wrong password shows `401`, `UNAUTHENTICATED`, `bad username or password` and keeps the form.
- After login: the username and role from `GET /api/me`, `expiresIn` (15 min) and a countdown.
  The token is kept in one JavaScript variable only: not in the URL, not in `localStorage`, not on the page.
- Move order: read (the dev demo order is filled in) or register; it shows the HTTP status and the status token.
- Complete: as `aliya` it shows `403 FORBIDDEN`, reads the order again and shows it is still
  `IN_PROGRESS`. As `nurlan` it shows `200` and `COMPLETED`; a second click shows `409 INVALID_TRANSITION`.
- Log out drops the variable and shows the form again; the next call from the page is a `401`, shown as such.
- Every call sends `X-Correlation-Id`; on 400, 401, 403 and 409 the page shows `code`, `message` and
  `correlationId` from the JSON, never a stack trace. A request log lists each call with "token sent" or
  "not sent", never the token.

| Step in the browser                                     | Result on the page                       |
|---------------------------------------------------------|------------------------------------------|
| open `/`                                                | Move Order Tracker, not a Spring login   |
| sign in as `aliya` with a wrong password                | 401 `UNAUTHENTICATED`, form still there  |
| sign in as `aliya` / `dispatcher-dev`                   | `aliya`, `DISPATCHER`, no raw JWT        |
| Read                                                    | 200, `IN_PROGRESS`                       |
| Complete                                                | 403, status still `IN_PROGRESS`          |
| Log out, sign in as `nurlan` / `manager-dev`, Complete   | 200, `COMPLETED`                         |
| Log out, Read                                           | 401 `valid token required`               |

### Real calls (`mvn spring-boot:run`)

```text
$ curl -i -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
       -d '{"username":"aliya","password":"dispatcher-dev"}'
HTTP/1.1 200
{"accessToken":"eyJhbGciOiJIUzI1NiJ9...","tokenType":"Bearer","expiresIn":900}

$ curl -i -X POST .../auth/login -H 'X-Correlation-Id: demo-1' -d '{"username":"aliya","password":"wrong"}'
HTTP/1.1 401
{"code":"UNAUTHENTICATED","message":"bad username or password","correlationId":"demo-1"}

$ curl -i -X POST .../auth/login -H 'X-Correlation-Id: demo-2' -d '{"username":"ghost","password":"wrong"}'
HTTP/1.1 401
{"code":"UNAUTHENTICATED","message":"bad username or password","correlationId":"demo-2"}

$ curl -i localhost:8080/api/v1/move-orders/5eed0001-2026-4000-8000-000000000001 -H 'X-Correlation-Id: demo-3'
HTTP/1.1 401
WWW-Authenticate: Bearer
{"code":"UNAUTHENTICATED","message":"valid token required","correlationId":"demo-3"}

$ curl -i localhost:8080/api/me -H "Authorization: Bearer $ALIYA"
HTTP/1.1 200
{"username":"aliya","authorities":["ROLE_DISPATCHER"]}

$ curl -i localhost:8080/api/v1/move-orders/5eed0001-... -H "Authorization: Bearer $ALIYA"
HTTP/1.1 200
{"id":"5eed0001-...","businessKey":"MO-2026-0001","status":"IN_PROGRESS","title":"Office move: Abay 10 -> Dostyk 5 (dev demo)"}

$ curl -i -X POST localhost:8080/api/v1/move-orders/5eed0001-.../complete -H "Authorization: Bearer $ALIYA" -H 'X-Correlation-Id: demo-4'
HTTP/1.1 403
{"code":"FORBIDDEN","message":"not allowed for your role","correlationId":"demo-4"}

$ curl -i -X POST localhost:8080/api/v1/move-orders/5eed0001-.../complete -H "Authorization: Bearer $NURLAN"
HTTP/1.1 200
{"id":"5eed0001-...","businessKey":"MO-2026-0001","status":"COMPLETED","title":"Office move: Abay 10 -> Dostyk 5 (dev demo)"}

$ curl -i -X POST localhost:8080/api/v1/move-orders/5eed0001-.../complete -H "Authorization: Bearer $NURLAN" -H 'X-Correlation-Id: demo-5'
HTTP/1.1 409
{"code":"INVALID_TRANSITION","message":"Move order cannot go from COMPLETED to COMPLETED","correlationId":"demo-5"}

$ curl -i localhost:8080/api/me -H 'Authorization: Bearer hello' -H 'X-Correlation-Id: demo-6'
HTTP/1.1 401
{"code":"UNAUTHENTICATED","message":"valid token required","correlationId":"demo-6"}

$ curl -i localhost:8080/actuator/env -H "Authorization: Bearer $NURLAN" -H 'X-Correlation-Id: demo-7'
HTTP/1.1 403
{"code":"FORBIDDEN","message":"not allowed for your role","correlationId":"demo-7"}
```

The log of the same run (the token never appears in it):

```text
SecurityErrors   : 403 POST /api/v1/move-orders/5eed0001-.../complete for aliya correlationId=demo-4
MoveOrderService : move order MO-2026-0001 (5eed0001-...) IN_PROGRESS -> COMPLETED by nurlan
```

### Contract

`/swagger-ui.html` opens without a token and has an **Authorize** button (`bearer-jwt` scheme):
paste the `accessToken` from the login. The statuses are named: login `200`, `400`, `401`; complete
`200`, `401`, `403` (and `400`, `404`, `409`); `GET /api/me` `200`, `401`; create and read add `401`.

### Tests

| Test                    | What it proves                                                                                   |
|-------------------------|--------------------------------------------------------------------------------------------------|
| `MoveOrderSecurityTest` | `@WebMvcTest` with the real `SecurityConfig`, service and `JwtDecoder` mocked, `jwt()` as the token: no token is 401 `UNAUTHENTICATED` and the service is not called; `DISPATCHER` on complete is 403 `FORBIDDEN` and the service is not called (also with `"role":"MANAGER"` in the body); `MANAGER` completes and the actor is the token's `sub`; a policy refusal is 409; both roles read; a token without our roles is 403; `/api/me` returns the token's user; `/actuator/health` (the real endpoint) is 200 without a token; anything else is 401 / 403 |
| `TokenServiceTest`      | no web layer: `TokenService` signs with a 32-byte test secret, `JwtConfig`'s decoder reads it back: `sub`, `roles` without `ROLE_`, `exp` in the future, HS256, `iss`, exactly 15 minutes, only five claims; another secret, an edited payload, another issuer, an expired token and a token without `exp` are rejected |
| `JwtSecretTest`         | `secret` and a missing secret fail at startup, 32 characters start; dev secret 32+ in yaml, prod is `${CSS_JWT_SECRET}`, none in `application.yaml`; no dev secret and no `{noop}` in `src/main/java` |
| `AuthFlowTest`          | whole app on PostgreSQL, real logins: token response and payload, one 401 for both bad logins, 400 for blanks, no password in the log; `/api/me`; dispatcher 403 and the row stays; manager 200 and the row is `COMPLETED`, the log names him and not the token; twice is 409; a `REQUESTED` order is 409; Lab 4 201 / 200 / 409 / 400 / 404 with a real token; `Bearer hello`, an edited role, an expired token, another issuer and Basic are 401; health, page, springdoc open; the contract lists 401 and 403 |
| `CompleteMoveOrderTest` | the second lock without HTTP: `@PreAuthorize` stops a dispatcher and an anonymous caller before the method runs; a manager writes `COMPLETED`; the policy still refuses `REQUESTED`; `dev-data.sql` resets the demo order |
| `MoveOrderControllerTest` | the Lab 4 slice, now with the chain and a dispatcher's `jwt()` on every call: 201, the 400s (service not called), 404, 409, 415, 500 as before; no token is 401, not 201 |
| `MoveOrderApiTest`      | Lab 4 on PostgreSQL with a token: `Location` readable, 409 keeps one row, a 400 stores nothing       |
| `DevProfileTest` / `ProdProfileTest` | dev: two users, one role each, `{bcrypt}` hashes that match; prod: secret from `CSS_JWT_SECRET`, no users, a login fails |
| `ActuatorTest`          | health and info open; `env`, `beans`, `heapdump`, `configprops`, `metrics`, `shutdown`: 401 without a token, 403 even for a manager |

## Configuration, profiles, health

Nothing that changes between a laptop and a server is written in Java. A grep of `src/main/java`
for `jdbc:postgresql` or `http://` finds nothing.

| File                     | Holds                                                                                      |
|--------------------------|--------------------------------------------------------------------------------------------|
| `application.yaml`       | what every run shares: `spring.profiles.default: dev`, the error-leak settings, actuator exposure, `info`. No URL, no password |
| `application-dev.yaml`   | the laptop: PostgreSQL `localhost:5432/css`, user and password `css` (classroom default); runs `db/schema.sql` and `db/dev-data.sql` on start; payment provider `http://localhost:9090`, timeout `2s`; the classroom JWT secret; the two dev users |
| `application-prod.yaml`  | a server: `CSS_DB_URL`, `CSS_DB_USER`, `CSS_DB_PASSWORD`, `CSS_DOWNSTREAM_URL`, `CSS_JWT_SECRET` from the environment. No default secret, no users |

A plain `mvn spring-boot:run` uses `dev`. A server runs with `SPRING_PROFILES_ACTIVE=prod` and the
five variables. The committed files never activate `prod`.

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
| `GET /actuator/env`, `/beans`, `/heapdump`, `POST /actuator/shutdown` | not exposed; since Lab 5 the chain answers first: **401** without a token, **403** with one |

### Tests

| Test              | What it proves                                                                          |
|-------------------|-----------------------------------------------------------------------------------------|
| `DevProfileTest`  | with `dev`, the base URL is `http://localhost:9090` and the timeout 2 seconds, `PaymentsClient` received them, the database is localhost `css`; other yaml binds other values. No socket to port 9090 |
| `ProdProfileTest` | with `prod`, the datasource and the base URL come from `CSS_*` variables                 |
| `ActuatorTest`    | health is 200 and UP without details, info carries the product name, `env`, `beans`, `heapdump`, `configprops`, `metrics`, `shutdown` are closed (401 / 403 since Lab 5) |

## Package diagram

```
  static/ (index.html, app.css, app.js): the auth page, same process
        |  POST /api/v1/auth/login, GET /api/me, /api/v1/move-orders..., Authorization: Bearer
        v
  [ filter chain: SecurityConfig  ->  401 / 403 written by handler.SecurityErrors ]
        |
  handler                      config                          persistence
  MoveOrderController   ---->  Application                     MoveOrderJdbc  @Repository
    POST/GET /api/v1/...       MoveOrderService  @Service  ->  (JdbcTemplate, ?, FOR UPDATE)
    POST .../{id}/complete       @Transactional, @PreAuthorize      |
  AuthController (login)  -->  TokenService (JwtEncoder)           |
  MeController (/api/me)       JwtConfig, JwtProperties (yaml)     |
  CreateMoveOrderRequest       UsersConfig (BCrypt, dev users)     |
  LoginRequest, TokenResponse  SecurityConfig, Roles               |
  MoveOrderResponse            MoveOrderRulesConfig  @Bean          |
  ErrorBody, ApiErrorAdvice    DownstreamProperties  (yaml)        |
  CorrelationId, SecurityErrors PaymentsConfig @Bean --> client: PaymentsClient
        |                      injects MoveOrderRule          implements DepositPayments
        v                             v                             v
                                       domain
          MoveOrderId  MoveOrderNumber  MoveOrderStatus  MoveOrderPolicy  MoveOrder
          MoveOrderRule + TransitionRule, DepositRequiredRule (MoveOrderRuleChain)
          DepositPayments (port), DepositConfirmation, PaymentsUnavailable
          DuplicateMoveOrder, MoveOrderNotFound, TransitionNotAllowed
                (no Spring, no Spring Security, no JDBC, no HTTP, no jakarta.validation)
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
| http://localhost:8080/                      | the auth page (dev users: `aliya` / `dispatcher-dev`, `nurlan` / `manager-dev`) |
| http://localhost:8080/swagger-ui.html       | the API, clickable           |
| http://localhost:8080/v3/api-docs           | the OpenAPI spec             |
| http://localhost:8080/actuator/health       | `{"status":"UP"}`            |
| http://localhost:8080/api/v1/move-orders    | `POST` here with a token (see REST API) |
| http://localhost:8080/api/v1/auth/login     | `POST` here to get the token |

Boundary checks (each prints nothing):

```bash
grep -rE "java\.sql|org\.springframework|jakarta\.validation|ResponseEntity" src/main/java/io/github/warfolame/moveorder/domain
grep -rn "dev-only-move-orders" src/main/java          # the dev JWT secret
grep -rnE "\{noop\}|formLogin\(Customizer|anyRequest\(\)\.permitAll" src/main
grep -rE "jdbc:postgresql|http://" src/main/java
grep -rniE "jakarta\.persistence|data-jpa" --include=*.java --include=pom.xml .
```
