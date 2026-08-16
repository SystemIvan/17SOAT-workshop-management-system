UPDATE customers
SET document = REPLACE(REPLACE(REPLACE(REPLACE(TRIM(document), '.', ''), '-', ''), '/', ''), ' ', '')
WHERE document IS NOT NULL;

ALTER TABLE customers MODIFY COLUMN name VARCHAR(255) NOT NULL;
ALTER TABLE customers MODIFY COLUMN document VARCHAR(14) NOT NULL;
ALTER TABLE customers MODIFY COLUMN contact_email VARCHAR(255) NOT NULL;
ALTER TABLE customers MODIFY COLUMN contact_phone VARCHAR(255) NOT NULL;
ALTER TABLE customers ADD CONSTRAINT uk_customers_document UNIQUE (document);
