CREATE TABLE catalog_services (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    normalized_name_key VARBINARY(1020) NOT NULL,
    base_price_value DECIMAL(19, 2) NOT NULL,
    base_price_currency CHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_catalog_services PRIMARY KEY (id),
    CONSTRAINT uk_catalog_services_normalized_name_key UNIQUE (normalized_name_key),
    CONSTRAINT ck_catalog_services_base_price_value CHECK (base_price_value >= 0),
    CONSTRAINT ck_catalog_services_base_price_currency CHECK (base_price_currency = 'BRL')
);
