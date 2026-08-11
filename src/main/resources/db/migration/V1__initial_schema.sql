CREATE TABLE customers (
    id BINARY(16) NOT NULL,
    name VARCHAR(255),
    document VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE parts (
    id BINARY(16) NOT NULL,
    name VARCHAR(255),
    sku VARCHAR(255),
    quantity INTEGER NOT NULL,
    price_value DECIMAL(38, 2),
    PRIMARY KEY (id)
);

CREATE TABLE technicians (
    id BINARY(16) NOT NULL,
    name VARCHAR(255),
    status VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE technician_specialties (
    technician_id BINARY(16) NOT NULL,
    specialty VARCHAR(255)
);

CREATE TABLE service_orders (
    id BINARY(16) NOT NULL,
    customer_id BINARY(16),
    vehicle_id BINARY(16),
    vehicle_license_plate VARCHAR(255),
    vehicle_brand VARCHAR(255),
    vehicle_model VARCHAR(255),
    vehicle_year INTEGER NOT NULL,
    priority VARCHAR(255),
    status_snapshot VARCHAR(255),
    open_diagnosis_id BINARY(16),
    has_sent_estimate_with_pending_lines BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE service_order_approved_estimates (
    service_order_id BINARY(16) NOT NULL,
    estimate_id BINARY(16)
);

CREATE TABLE service_executions (
    id BINARY(16) NOT NULL,
    service_order_id BINARY(16) NOT NULL,
    diagnosis_id BINARY(16),
    catalog_service_id BINARY(16),
    name VARCHAR(255),
    price_value DECIMAL(38, 2),
    price_currency VARCHAR(255),
    status VARCHAR(255),
    authorized_by_estimate_id BINARY(16),
    assigned_technician_id BINARY(16),
    PRIMARY KEY (id)
);

CREATE TABLE service_execution_stock_requirements (
    service_execution_id BINARY(16) NOT NULL,
    stock_item_id BINARY(16),
    type VARCHAR(255),
    quantity INTEGER NOT NULL,
    name_snapshot VARCHAR(255),
    price_snapshot_value DECIMAL(38, 2),
    price_snapshot_currency VARCHAR(255),
    reserved BOOLEAN NOT NULL
);

ALTER TABLE technician_specialties
    ADD CONSTRAINT fk_technician_specialties_technician
    FOREIGN KEY (technician_id) REFERENCES technicians (id);

ALTER TABLE service_order_approved_estimates
    ADD CONSTRAINT fk_service_order_approved_estimates_order
    FOREIGN KEY (service_order_id) REFERENCES service_orders (id);

ALTER TABLE service_executions
    ADD CONSTRAINT fk_service_executions_order
    FOREIGN KEY (service_order_id) REFERENCES service_orders (id);

ALTER TABLE service_execution_stock_requirements
    ADD CONSTRAINT fk_service_execution_stock_requirements_execution
    FOREIGN KEY (service_execution_id) REFERENCES service_executions (id);
