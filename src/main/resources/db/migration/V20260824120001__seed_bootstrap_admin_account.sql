-- Mandatory reference data (not a business/demo seed): the system needs at least one ADMIN
-- credential to exist before anyone can authenticate and create further accounts via
-- POST /api/auth/users, which itself requires an ADMIN token. Without this row the
-- identity module would have no bootstrap path.
--
-- Username: admin
-- Password: changeme123 (bcrypt hash below) — SECURITY: rotate this password immediately in
-- any environment beyond local development/demonstration. See technical-spec.md and
-- implementation-plan.md ("Revisão de segurança") for jwt-authentication.
INSERT INTO user_accounts (id, username, password_hash, role, linked_domain_id, created_at)
VALUES (
    X'00000000000000000000000000000001',
    'admin',
    '$2a$10$PWvMFBK0Ejd5gTQlsXgD6OTDmkSpREvFr8zu8LYx3WhojU7j7EN8W',
    'ADMIN',
    NULL,
    CURRENT_TIMESTAMP
);
