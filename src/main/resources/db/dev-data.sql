-- Dev profile only (spring.sql.init.data-locations in application-dev.yaml), after schema.sql.
-- One move order that is already IN_PROGRESS, so the auth page can show a MANAGER completing it:
-- a new order starts in REQUESTED, and the status table only lets IN_PROGRESS go to COMPLETED.
-- Every start puts it back to IN_PROGRESS with this fixed id, so the demo repeats after a restart.
INSERT INTO move_order (id, business_key, status, title, deposit_paid)
VALUES ('5eed0001-2026-4000-8000-000000000001', 'MO-2026-0001', 'IN_PROGRESS',
        'Office move: Abay 10 -> Dostyk 5 (dev demo)', true)
ON CONFLICT (business_key) DO UPDATE
  SET id = EXCLUDED.id, status = 'IN_PROGRESS', deposit_paid = true, title = EXCLUDED.title;
