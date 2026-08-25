CREATE TABLE purchase_orders (
    id BINARY(16) NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    external_reference VARCHAR(255) NULL,
    supplier_rejection_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    opened_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_purchase_orders_external_reference UNIQUE (external_reference),
    CONSTRAINT ck_purchase_orders_status CHECK (status IN ('PENDING_SUBMISSION', 'OPEN', 'REJECTED')),
    CONSTRAINT ck_purchase_orders_payload_hash CHECK (CHAR_LENGTH(payload_hash) = 64),
    CONSTRAINT ck_purchase_orders_state CHECK (
        (status = 'PENDING_SUBMISSION'
            AND external_reference IS NULL AND supplier_rejection_code IS NULL AND opened_at IS NULL)
        OR (status = 'OPEN'
            AND external_reference IS NOT NULL AND supplier_rejection_code IS NULL AND opened_at IS NOT NULL)
        OR (status = 'REJECTED'
            AND external_reference IS NULL AND supplier_rejection_code IS NOT NULL AND opened_at IS NULL)
    )
);

CREATE TABLE purchase_demands (
    id BINARY(16) NOT NULL,
    origin VARCHAR(32) NOT NULL,
    origin_reference_id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    requested_quantity INTEGER NULL,
    observed_available_quantity INTEGER NOT NULL,
    suggested_quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    claimed_by_purchase_order_id BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_demands_origin_reference_item
        UNIQUE (origin, origin_reference_id, stock_item_id),
    CONSTRAINT fk_purchase_demands_stock_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_items (id),
    CONSTRAINT fk_purchase_demands_claimed_order
        FOREIGN KEY (claimed_by_purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT ck_purchase_demands_origin CHECK (origin IN ('PENDING_REPAIR', 'LOW_STOCK')),
    CONSTRAINT ck_purchase_demands_status CHECK (status IN ('OPEN', 'CLAIMED', 'ORDERED', 'RESOLVED')),
    CONSTRAINT ck_purchase_demands_quantities CHECK (
        observed_available_quantity >= 0 AND suggested_quantity > 0
        AND ((origin = 'PENDING_REPAIR' AND requested_quantity > 0)
            OR (origin = 'LOW_STOCK' AND requested_quantity IS NULL))
    ),
    CONSTRAINT ck_purchase_demands_state CHECK (
        (status = 'CLAIMED' AND claimed_by_purchase_order_id IS NOT NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND claimed_by_purchase_order_id IS NULL AND resolved_at IS NOT NULL)
        OR (status IN ('OPEN', 'ORDERED') AND claimed_by_purchase_order_id IS NULL AND resolved_at IS NULL)
    )
);

CREATE INDEX idx_purchase_demands_status_created
    ON purchase_demands (status, created_at, id);
CREATE INDEX idx_purchase_demands_origin_status
    ON purchase_demands (origin, status);
CREATE INDEX idx_purchase_demands_stock_item_status
    ON purchase_demands (stock_item_id, status);

CREATE TABLE purchase_order_lines (
    purchase_order_id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    PRIMARY KEY (purchase_order_id, stock_item_id),
    CONSTRAINT fk_purchase_order_lines_order
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_order_lines_stock_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_items (id),
    CONSTRAINT ck_purchase_order_lines_type CHECK (type IN ('PART', 'CONSUMABLE', 'SUPPLY')),
    CONSTRAINT ck_purchase_order_lines_quantity CHECK (quantity > 0)
);

CREATE TABLE purchase_order_demand_links (
    purchase_order_id BINARY(16) NOT NULL,
    purchase_demand_id BINARY(16) NOT NULL,
    PRIMARY KEY (purchase_order_id, purchase_demand_id),
    CONSTRAINT fk_purchase_order_demand_links_order
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_order_demand_links_demand
        FOREIGN KEY (purchase_demand_id) REFERENCES purchase_demands (id)
);
