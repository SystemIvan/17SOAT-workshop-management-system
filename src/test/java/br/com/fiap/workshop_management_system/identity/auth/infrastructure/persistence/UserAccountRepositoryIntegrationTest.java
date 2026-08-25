package br.com.fiap.workshop_management_system.identity.auth.infrastructure.persistence;

import br.com.fiap.workshop_management_system.identity.auth.application.exception.DuplicateUsernameException;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class UserAccountRepositoryIntegrationTest {

    @Autowired
    private UserAccountRepository repository;

    @Test
    void persistsAndRestoresAnAccountLinkedToADomainAggregate() {
        UUID linkedDomainId = UUID.randomUUID();
        Username username = new Username("technician." + UUID.randomUUID());
        UserAccount account = UserAccount.create(
                username, "$2a$10$hashvalue", Role.TECHNICIAN, linkedDomainId, Instant.parse("2026-08-24T12:00:00Z"));

        repository.save(account);

        UserAccount restored = repository.findByUsername(username).orElseThrow();
        assertEquals(account.id(), restored.id());
        assertEquals(Role.TECHNICIAN, restored.role());
        assertEquals(linkedDomainId, restored.linkedDomainId());
        assertTrue(repository.existsByUsername(username));
    }

    @Test
    void persistsAnAccountWithoutALinkedDomainId() {
        Username username = new Username("admin." + UUID.randomUUID());
        UserAccount account = UserAccount.create(
                username, "$2a$10$hashvalue", Role.ADMIN, null, Instant.parse("2026-08-24T12:00:00Z"));

        repository.save(account);

        UserAccount restored = repository.findByUsername(username).orElseThrow();
        assertEquals(Role.ADMIN, restored.role());
        assertEquals(null, restored.linkedDomainId());
    }

    @Test
    void databaseUniquenessProtectsUsernameDuringConcurrentLikeSaves() {
        Username username = new Username("manager." + UUID.randomUUID());
        repository.save(UserAccount.create(
                username, "$2a$10$firstHash", Role.MANAGER, null, Instant.parse("2026-08-24T12:00:00Z")));

        assertThrows(DuplicateUsernameException.class,
                () -> repository.save(UserAccount.create(
                        username, "$2a$10$secondHash", Role.MANAGER, null, Instant.parse("2026-08-24T12:00:00Z"))));
    }

    @Test
    void findByUsernameReturnsEmptyForUnknownUsername() {
        assertFalse(repository.findByUsername(new Username("unknown." + UUID.randomUUID())).isPresent());
    }
}
