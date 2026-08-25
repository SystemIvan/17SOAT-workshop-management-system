ALTER TABLE catalog_services
    ADD COLUMN active_normalized_name_key VARBINARY(1020)
        GENERATED ALWAYS AS (
            CASE WHEN active THEN normalized_name_key ELSE NULL END
        );

ALTER TABLE catalog_services
    ADD CONSTRAINT uk_catalog_services_active_normalized_name_key
        UNIQUE (active_normalized_name_key);

ALTER TABLE catalog_services
    DROP INDEX uk_catalog_services_normalized_name_key;
