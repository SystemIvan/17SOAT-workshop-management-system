CREATE TABLE user_accounts (
    id BINARY(16) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    linked_domain_id BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_username UNIQUE (username),
    CONSTRAINT ck_user_accounts_role CHECK (role IN ('CUSTOMER', 'TECHNICIAN', 'MANAGER', 'ADMIN'))
);
