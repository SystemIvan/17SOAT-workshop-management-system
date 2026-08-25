CREATE TABLE service_execution_stock_availability (
    service_execution_id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    requested_quantity INTEGER NOT NULL,
    observed_available_quantity INTEGER NOT NULL,
    shortage_quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (service_execution_id, stock_item_id),
    CONSTRAINT fk_service_execution_stock_availability_execution
        FOREIGN KEY (service_execution_id) REFERENCES service_executions (id),
    CONSTRAINT ck_service_execution_stock_availability_quantities CHECK (
        requested_quantity > 0 AND observed_available_quantity >= 0 AND shortage_quantity >= 0
        AND shortage_quantity = GREATEST(requested_quantity - observed_available_quantity, 0)
    ),
    CONSTRAINT ck_service_execution_stock_availability_status CHECK (
        (status = 'AVAILABLE' AND shortage_quantity = 0)
        OR (status = 'INSUFFICIENT_QUANTITY' AND shortage_quantity > 0)
    )
);

CREATE TABLE estimate_line_stock_availability (
    estimate_line_id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    requested_quantity INTEGER NOT NULL,
    observed_available_quantity INTEGER NOT NULL,
    shortage_quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (estimate_line_id, stock_item_id),
    CONSTRAINT fk_estimate_line_stock_availability_line
        FOREIGN KEY (estimate_line_id) REFERENCES estimate_lines (id),
    CONSTRAINT ck_estimate_line_stock_availability_quantities CHECK (
        requested_quantity > 0 AND observed_available_quantity >= 0 AND shortage_quantity >= 0
        AND shortage_quantity = GREATEST(requested_quantity - observed_available_quantity, 0)
    ),
    CONSTRAINT ck_estimate_line_stock_availability_status CHECK (
        (status = 'AVAILABLE' AND shortage_quantity = 0)
        OR (status = 'INSUFFICIENT_QUANTITY' AND shortage_quantity > 0)
    )
);
