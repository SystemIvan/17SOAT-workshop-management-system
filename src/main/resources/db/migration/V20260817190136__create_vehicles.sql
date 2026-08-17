CREATE TABLE vehicles (
    id BINARY(16) NOT NULL,
    customer_id BINARY(16) NOT NULL,
    license_plate VARCHAR(7) NOT NULL,
    chassis_number VARCHAR(17) NULL,
    brand VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    model_year INTEGER NOT NULL,
    color VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_vehicles_license_plate UNIQUE (license_plate),
    CONSTRAINT uk_vehicles_chassis_number UNIQUE (chassis_number),
    CONSTRAINT ck_vehicles_model_year_min CHECK (model_year >= 1886),
    CONSTRAINT fk_vehicles_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE INDEX idx_vehicles_customer_id ON vehicles (customer_id);
