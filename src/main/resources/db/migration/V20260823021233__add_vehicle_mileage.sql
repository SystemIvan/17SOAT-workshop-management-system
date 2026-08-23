ALTER TABLE vehicles
    ADD COLUMN mileage BIGINT NULL;

ALTER TABLE vehicles
    ADD CONSTRAINT ck_vehicles_mileage_non_negative CHECK (mileage IS NULL OR mileage >= 0);
