ALTER TABLE customers ADD COLUMN address_street VARCHAR(255) NULL;
ALTER TABLE customers ADD COLUMN address_number VARCHAR(255) NULL;
ALTER TABLE customers ADD COLUMN address_complement VARCHAR(255) NULL;
ALTER TABLE customers ADD COLUMN address_neighborhood VARCHAR(255) NULL;
ALTER TABLE customers ADD COLUMN address_city VARCHAR(255) NULL;
ALTER TABLE customers ADD COLUMN address_state VARCHAR(2) NULL;
ALTER TABLE customers ADD COLUMN address_postal_code VARCHAR(8) NULL;

ALTER TABLE customers ADD CONSTRAINT ck_customers_address_complete CHECK (
    (address_street IS NULL
        AND address_number IS NULL
        AND address_complement IS NULL
        AND address_neighborhood IS NULL
        AND address_city IS NULL
        AND address_state IS NULL
        AND address_postal_code IS NULL)
    OR
    (address_street IS NOT NULL
        AND address_number IS NOT NULL
        AND address_city IS NOT NULL
        AND address_state IS NOT NULL
        AND address_postal_code IS NOT NULL)
);
