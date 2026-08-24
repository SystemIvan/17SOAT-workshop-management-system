package br.com.fiap.workshop_management_system.identity.auth.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserAccountTest {

    private static final Username USERNAME = new Username("jane.doe");
    private static final String PASSWORD_HASH = "$2a$10$hashvalue";
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T12:00:00Z");

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "TECHNICIAN"})
    void createsSuccessfullyWithLinkedDomainIdForDomainBackedRoles(Role role) {
        UUID linkedDomainId = UUID.randomUUID();

        UserAccount account = UserAccount.create(USERNAME, PASSWORD_HASH, role, linkedDomainId, CREATED_AT);

        assertEquals(USERNAME, account.username());
        assertEquals(role, account.role());
        assertEquals(linkedDomainId, account.linkedDomainId());
        assertEquals(CREATED_AT, account.createdAt());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"MANAGER", "ADMIN"})
    void createsSuccessfullyWithoutLinkedDomainIdForPlatformRoles(Role role) {
        UserAccount account = UserAccount.create(USERNAME, PASSWORD_HASH, role, null, CREATED_AT);

        assertEquals(role, account.role());
        assertNull(account.linkedDomainId());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "TECHNICIAN"})
    void rejectsDomainBackedRoleWithoutLinkedDomainId(Role role) {
        assertThrows(InvalidUserAccountException.class,
                () -> UserAccount.create(USERNAME, PASSWORD_HASH, role, null, CREATED_AT));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"MANAGER", "ADMIN"})
    void rejectsPlatformRoleWithLinkedDomainId(Role role) {
        UUID linkedDomainId = UUID.randomUUID();

        assertThrows(InvalidUserAccountException.class,
                () -> UserAccount.create(USERNAME, PASSWORD_HASH, role, linkedDomainId, CREATED_AT));
    }

    @Test
    void rejectsNullUsername() {
        assertThrows(InvalidUserAccountException.class,
                () -> UserAccount.create(null, PASSWORD_HASH, Role.ADMIN, null, CREATED_AT));
    }

    @Test
    void rejectsBlankPasswordHash() {
        assertThrows(InvalidUserAccountException.class,
                () -> UserAccount.create(USERNAME, "  ", Role.ADMIN, null, CREATED_AT));
    }

    @Test
    void rejectsNullRole() {
        assertThrows(InvalidUserAccountException.class,
                () -> UserAccount.create(USERNAME, PASSWORD_HASH, null, null, CREATED_AT));
    }

    @Test
    void reconstitutePreservesPersistedState() {
        UUID id = UUID.randomUUID();
        UUID linkedDomainId = UUID.randomUUID();

        UserAccount account = UserAccount.reconstitute(
                id, USERNAME, PASSWORD_HASH, Role.CUSTOMER, linkedDomainId, CREATED_AT);

        assertEquals(id, account.id());
        assertEquals(USERNAME, account.username());
        assertEquals(PASSWORD_HASH, account.passwordHash());
        assertEquals(Role.CUSTOMER, account.role());
        assertEquals(linkedDomainId, account.linkedDomainId());
        assertEquals(CREATED_AT, account.createdAt());
    }
}
