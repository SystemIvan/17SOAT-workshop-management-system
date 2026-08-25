package br.com.fiap.workshop_management_system.identity.auth.application.usecase;

import br.com.fiap.workshop_management_system.identity.auth.application.dto.CreateUserAccountRequest;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.UserAccountResponse;
import br.com.fiap.workshop_management_system.identity.auth.application.exception.DuplicateUsernameException;
import br.com.fiap.workshop_management_system.identity.auth.application.port.PasswordHasher;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateUserAccountUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final CreateUserAccountUseCase useCase =
            new CreateUserAccountUseCase(repository, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsAnAccountWithTheHashedPasswordNeverTheRawOne() {
        when(repository.existsByUsername(new Username("jane.doe"))).thenReturn(false);
        when(passwordHasher.hash("raw-password")).thenReturn("$2a$10$hashvalue");

        UserAccountResponse response = useCase.execute(
                new CreateUserAccountRequest("jane.doe", "raw-password", Role.MANAGER, null));

        assertEquals("jane.doe", response.username());
        assertEquals(Role.MANAGER, response.role());

        var captor = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(captor.capture());
        assertEquals("$2a$10$hashvalue", captor.getValue().passwordHash());
    }

    @Test
    void rejectsADuplicateUsernameWithoutHashingOrSavingAnything() {
        when(repository.existsByUsername(new Username("jane.doe"))).thenReturn(true);

        assertThrows(DuplicateUsernameException.class,
                () -> useCase.execute(new CreateUserAccountRequest("jane.doe", "raw-password", Role.ADMIN, null)));

        verify(passwordHasher, never()).hash(any());
        verify(repository, never()).save(any());
    }

    @Test
    void createsAnAccountLinkedToADomainAggregate() {
        UUID linkedDomainId = UUID.randomUUID();
        when(repository.existsByUsername(new Username("john.tech"))).thenReturn(false);
        when(passwordHasher.hash("raw-password")).thenReturn("$2a$10$hashvalue");

        UserAccountResponse response = useCase.execute(
                new CreateUserAccountRequest("john.tech", "raw-password", Role.TECHNICIAN, linkedDomainId));

        assertEquals(linkedDomainId, response.linkedDomainId());
    }
}
