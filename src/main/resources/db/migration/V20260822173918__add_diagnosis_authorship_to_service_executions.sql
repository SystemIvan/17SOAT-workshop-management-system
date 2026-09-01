ALTER TABLE service_executions ADD COLUMN diagnosed_by_technician_id BINARY(16) NULL;
ALTER TABLE service_executions ADD COLUMN diagnosed_at TIMESTAMP(6) NULL;
