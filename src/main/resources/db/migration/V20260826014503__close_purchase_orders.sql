ALTER TABLE purchase_orders
    ADD COLUMN closed_at TIMESTAMP(6) NULL;

ALTER TABLE purchase_orders
    ADD COLUMN closed_by_user_account_id BINARY(16) NULL;

ALTER TABLE purchase_orders DROP CONSTRAINT ck_purchase_orders_status;
ALTER TABLE purchase_orders DROP CONSTRAINT ck_purchase_orders_state;

ALTER TABLE purchase_orders
    ADD CONSTRAINT ck_purchase_orders_status
        CHECK (status IN ('PENDING_SUBMISSION', 'OPEN', 'REJECTED', 'CLOSED'));

ALTER TABLE purchase_orders
    ADD CONSTRAINT ck_purchase_orders_state CHECK (
        (status = 'PENDING_SUBMISSION'
            AND external_reference IS NULL AND supplier_rejection_code IS NULL AND opened_at IS NULL
            AND closed_at IS NULL AND closed_by_user_account_id IS NULL)
        OR (status = 'OPEN'
            AND external_reference IS NOT NULL AND supplier_rejection_code IS NULL AND opened_at IS NOT NULL
            AND closed_at IS NULL AND closed_by_user_account_id IS NULL)
        OR (status = 'REJECTED'
            AND external_reference IS NULL AND supplier_rejection_code IS NOT NULL AND opened_at IS NULL
            AND closed_at IS NULL AND closed_by_user_account_id IS NULL)
        OR (status = 'CLOSED'
            AND external_reference IS NOT NULL AND supplier_rejection_code IS NULL AND opened_at IS NOT NULL
            AND closed_at IS NOT NULL AND closed_by_user_account_id IS NOT NULL
            AND closed_at >= opened_at AND updated_at = closed_at)
    );

CREATE INDEX idx_purchase_orders_status_updated ON purchase_orders (status, updated_at, id);
