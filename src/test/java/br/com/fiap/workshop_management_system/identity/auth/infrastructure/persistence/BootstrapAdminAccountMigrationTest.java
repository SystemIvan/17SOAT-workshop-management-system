package br.com.fiap.workshop_management_system.identity.auth.infrastructure.persistence;

import br.com.fiap.workshop_management_system.identity.auth.application.port.PasswordHasher;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies V20260824120001__seed_bootstrap_admin_account.sql: the ADMIN account required to
 * bootstrap POST /api/auth/users must exist and its documented password must actually match the
 * stored hash (a wrong/malformed hash would make the row silently unusable).
 */
@SpringBootTest
class BootstrapAdminAccountMigrationTest {

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void bootstrapAdminAccountExistsWithTheDocumentedPassword() {
        UserAccount admin = repository.findByUsername(new Username("admin")).orElseThrow();

        assertTrue(passwordHasher.matches("changeme123", admin.passwordHash()));
        assertNull(admin.linkedDomainId());
        assertEquals(Role.ADMIN, admin.role());
    }
}
