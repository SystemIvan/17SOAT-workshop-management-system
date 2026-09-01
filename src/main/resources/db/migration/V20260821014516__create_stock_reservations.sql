CREATE TABLE stock_reservations (
    id BINARY(16) NOT NULL,
    service_execution_id BINARY(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_reservations_service_execution UNIQUE (service_execution_id),
    CONSTRAINT ck_stock_reservations_status CHECK (status IN ('ACTIVE', 'CONSUMED')),
    CONSTRAINT ck_stock_reservations_consumption CHECK (
        (status = 'ACTIVE' AND consumed_at IS NULL)
        OR (status = 'CONSUMED' AND consumed_at IS NOT NULL)
    )
);

CREATE TABLE stock_reservation_lines (
    reservation_id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    quantity INTEGER NOT NULL,
    PRIMARY KEY (reservation_id, stock_item_id),
    CONSTRAINT fk_stock_reservation_lines_reservation
        FOREIGN KEY (reservation_id) REFERENCES stock_reservations (id),
    CONSTRAINT fk_stock_reservation_lines_stock_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_items (id),
    CONSTRAINT ck_stock_reservation_lines_quantity CHECK (quantity > 0)
);

ALTER TABLE service_executions
    ADD COLUMN stock_requirements_frozen BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE service_executions
    ADD COLUMN stock_reservation_id BINARY(16) NULL;

UPDATE service_executions
SET stock_requirements_frozen = TRUE
WHERE id IN (SELECT service_execution_id FROM estimate_lines);

UPDATE service_execution_stock_requirements
SET reserved = FALSE;

UPDATE service_executions
SET status = 'AWAITING_ITEMS'
WHERE status = 'AWAITING_PART';

UPDATE service_executions
SET status = 'AWAITING_ITEMS'
WHERE status = 'READY'
  AND EXISTS (
      SELECT 1
      FROM service_execution_stock_requirements requirement
      WHERE requirement.service_execution_id = service_executions.id
  );

UPDATE service_orders
SET status_snapshot = 'AWAITING_ITEMS'
WHERE status_snapshot = 'AWAITING_PART';

UPDATE service_orders
SET status_snapshot = 'AWAITING_ITEMS'
WHERE status_snapshot IN ('RECEIVED', 'IN_DIAGNOSIS', 'AWAITING_APPROVAL')
  AND EXISTS (
      SELECT 1
      FROM service_executions execution
      WHERE execution.service_order_id = service_orders.id
        AND execution.status = 'AWAITING_ITEMS'
  );
