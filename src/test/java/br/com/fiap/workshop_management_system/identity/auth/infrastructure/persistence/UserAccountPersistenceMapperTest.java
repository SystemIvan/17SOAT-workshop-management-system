package br.com.fiap.workshop_management_system.identity.auth.infrastructure.persistence;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserAccountPersistenceMapperTest {

    private final UserAccountPersistenceMapper mapper = new UserAccountPersistenceMapper();

    @ParameterizedTest
    @EnumSource(Role.class)
    void roundTripsEveryRole(Role role) {
        UUID linkedDomainId = (role == Role.CUSTOMER || role == Role.TECHNICIAN) ? UUID.randomUUID() : null;
        UserAccount account = UserAccount.reconstitute(
                UUID.randomUUID(),
                new Username("user." + role),
                "$2a$10$hashvalue",
                role,
                linkedDomainId,
                Instant.parse("2026-08-24T12:00:00Z"));

        UserAccountJpaEntity entity = mapper.toEntity(account);
        UserAccount reconstituted = mapper.toDomain(entity);

        assertEquals(role, entity.getRole());
        assertEquals(role, reconstituted.role());
        assertEquals(account.id(), reconstituted.id());
        assertEquals(account.username(), reconstituted.username());
        assertEquals(account.passwordHash(), reconstituted.passwordHash());
        assertEquals(account.createdAt(), reconstituted.createdAt());
        if (linkedDomainId == null) {
            assertNull(reconstituted.linkedDomainId());
        } else {
            assertEquals(linkedDomainId, reconstituted.linkedDomainId());
        }
    }
}
