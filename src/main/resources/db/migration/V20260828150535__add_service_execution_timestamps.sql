ALTER TABLE service_executions ADD COLUMN started_at TIMESTAMP(6) NULL;
ALTER TABLE service_executions ADD COLUMN completed_at TIMESTAMP(6) NULL;

ALTER TABLE service_executions
    ADD CONSTRAINT ck_service_executions_execution_times
    CHECK (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at));

CREATE INDEX idx_service_executions_execution_time_metrics
    ON service_executions (status, completed_at, catalog_service_id);
