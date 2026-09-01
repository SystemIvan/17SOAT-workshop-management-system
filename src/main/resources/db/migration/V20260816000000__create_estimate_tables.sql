CREATE TABLE estimates (
    id BINARY(16) NOT NULL,
    service_order_id BINARY(16) NOT NULL,
    diagnosis_id BINARY(16) NOT NULL,
    customer_id BINARY(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_estimates_diagnosis_id UNIQUE (diagnosis_id)
);

CREATE TABLE estimate_lines (
    id BINARY(16) NOT NULL,
    estimate_id BINARY(16) NOT NULL,
    line_order INTEGER NOT NULL,
    service_execution_id BINARY(16) NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    service_price_value DECIMAL(38, 2) NOT NULL,
    service_price_currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_estimate_lines_estimate
        FOREIGN KEY (estimate_id) REFERENCES estimates (id)
);

CREATE TABLE estimate_stock_items (
    id BINARY(16) NOT NULL,
    estimate_line_id BINARY(16) NOT NULL,
    item_order INTEGER NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    type VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    name_snapshot VARCHAR(255) NOT NULL,
    price_snapshot_value DECIMAL(38, 2) NOT NULL,
    price_snapshot_currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_estimate_stock_items_line
        FOREIGN KEY (estimate_line_id) REFERENCES estimate_lines (id),
    CONSTRAINT ck_estimate_stock_items_quantity
        CHECK (quantity > 0),
    CONSTRAINT ck_estimate_stock_items_type
        CHECK (type IN ('PART', 'CONSUMABLE', 'SUPPLY'))
);

CREATE INDEX idx_estimate_lines_estimate_id
    ON estimate_lines (estimate_id);

CREATE INDEX idx_estimate_stock_items_line_id
    ON estimate_stock_items (estimate_line_id);