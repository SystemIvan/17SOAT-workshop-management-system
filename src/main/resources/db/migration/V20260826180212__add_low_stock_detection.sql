ALTER TABLE stock_items ADD COLUMN minimum_quantity INTEGER NULL;

ALTER TABLE stock_items ADD COLUMN target_quantity INTEGER NULL;

ALTER TABLE stock_items ADD CONSTRAINT ck_stock_items_low_stock_policy CHECK (
    (minimum_quantity IS NULL AND target_quantity IS NULL)
    OR (minimum_quantity >= 0 AND target_quantity > minimum_quantity)
);

CREATE INDEX idx_stock_items_low_stock_detection
    ON stock_items (active, minimum_quantity, available_quantity);

CREATE TABLE low_stock_occurrences (
    id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    purchase_demand_id BINARY(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    open_slot INTEGER NULL,
    observed_available_quantity INTEGER NOT NULL,
    suggested_quantity INTEGER NOT NULL,
    detected_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    closed_at TIMESTAMP(6) NULL,
    closure_reason VARCHAR(48) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_low_stock_occurrences_open_slot UNIQUE (stock_item_id, open_slot),
    CONSTRAINT uk_low_stock_occurrences_purchase_demand UNIQUE (purchase_demand_id),
    CONSTRAINT fk_low_stock_occurrences_stock_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_items (id),
    CONSTRAINT fk_low_stock_occurrences_purchase_demand
        FOREIGN KEY (purchase_demand_id) REFERENCES purchase_demands (id),
    CONSTRAINT ck_low_stock_occurrences_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_low_stock_occurrences_quantities CHECK (
        observed_available_quantity >= 0 AND suggested_quantity > 0
    ),
    CONSTRAINT ck_low_stock_occurrences_timeline CHECK (updated_at >= detected_at),
    CONSTRAINT ck_low_stock_occurrences_state CHECK (
        (status = 'OPEN' AND open_slot = 1 AND closed_at IS NULL AND closure_reason IS NULL)
        OR (status = 'CLOSED' AND open_slot IS NULL AND closed_at = updated_at AND closure_reason IS NOT NULL)
    )
);

CREATE INDEX idx_low_stock_occurrences_item_detected
    ON low_stock_occurrences (stock_item_id, detected_at, id);
