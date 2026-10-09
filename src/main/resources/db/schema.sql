-- One move order. Written by hand; nothing generates this table.
--   id            surrogate key: the UUID from MoveOrderId.newId()
--   business_key  the order number people see (MO-2026-0042); one order per number
--   status        one of the four MoveOrderStatus values from the README
--   deposit_paid  a fact about money (DepositRequiredRule), not a second status
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
