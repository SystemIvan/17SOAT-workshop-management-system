ALTER TABLE service_orders ADD COLUMN created_at TIMESTAMP(6) NULL;

UPDATE service_orders SET created_at = CURRENT_TIMESTAMP(6) WHERE created_at IS NULL;

ALTER TABLE service_orders MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL;

CREATE INDEX idx_service_orders_status_created_at ON service_orders (status_snapshot, created_at);
