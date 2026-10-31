CREATE INDEX idx_estimates_service_order_id_status
    ON estimates (service_order_id, status);
