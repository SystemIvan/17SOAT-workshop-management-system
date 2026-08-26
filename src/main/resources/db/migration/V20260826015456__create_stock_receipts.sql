CREATE TABLE stock_receipts (
    id BINARY(16) NOT NULL,
    purchase_order_id BINARY(16) NOT NULL,
    received_by_user_account_id BINARY(16) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_receipts_purchase_order UNIQUE (purchase_order_id),
    CONSTRAINT fk_stock_receipts_purchase_order
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id)
);

CREATE TABLE stock_receipt_lines (
    movement_id BINARY(16) NOT NULL,
    stock_receipt_id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    quantity INTEGER NOT NULL,
    available_before INTEGER NOT NULL,
    available_after INTEGER NOT NULL,
    PRIMARY KEY (movement_id),
    CONSTRAINT uk_stock_receipt_lines_receipt_item UNIQUE (stock_receipt_id, stock_item_id),
    CONSTRAINT fk_stock_receipt_lines_receipt
        FOREIGN KEY (stock_receipt_id) REFERENCES stock_receipts (id),
    CONSTRAINT fk_stock_receipt_lines_stock_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_items (id),
    CONSTRAINT ck_stock_receipt_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_receipt_lines_balances CHECK (
        available_before >= 0 AND available_after >= 0 AND available_after - available_before = quantity
    )
);

CREATE INDEX idx_stock_receipt_lines_stock_item ON stock_receipt_lines (stock_item_id);
CREATE INDEX idx_stock_receipts_received_at ON stock_receipts (received_at);
