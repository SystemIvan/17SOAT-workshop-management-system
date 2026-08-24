package br.com.fiap.workshop_management_system.identity.auth.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.identity.auth.application.port.PasswordHasher;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityDevelopmentDataSeederTest {

    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);

    @Test
    void seedsOneAccountPerRoleOnlyOnceWhenRunRepeatedly() {
        when(passwordHasher.hash("changeme123")).thenReturn("$2a$10$hashvalue");
        InMemoryUserAccountRepository repository = new InMemoryUserAccountRepository();
        IdentityDevelopmentDataSeeder seeder = new IdentityDevelopmentDataSeeder(
                repository, passwordHasher, Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC));

        seeder.run(null);
        seeder.run(null);

        assertEquals(3, repository.accounts.size());
        assertTrue(repository.accounts.stream().anyMatch(a -> a.role() == Role.MANAGER && a.linkedDomainId() == null));
        assertTrue(repository.accounts.stream().anyMatch(a -> a.role() == Role.TECHNICIAN && a.linkedDomainId() != null));
        assertTrue(repository.accounts.stream().anyMatch(a -> a.role() == Role.CUSTOMER && a.linkedDomainId() != null));
    }

    private static final class InMemoryUserAccountRepository implements UserAccountRepository {

        private final List<UserAccount> accounts = new ArrayList<>();

        @Override
        public Optional<UserAccount> findById(UUID id) {
            return accounts.stream().filter(account -> account.id().equals(id)).findFirst();
        }

        @Override
        public Optional<UserAccount> findByUsername(Username username) {
            return accounts.stream().filter(account -> account.username().equals(username)).findFirst();
        }

        @Override
        public boolean existsByUsername(Username username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public void save(UserAccount account) {
            accounts.add(account);
        }
    }
}
