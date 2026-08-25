package br.com.fiap.workshop_management_system.identity.auth.application.usecase;

import br.com.fiap.workshop_management_system.identity.auth.application.dto.IssuedTokenResponse;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.LoginRequest;
import br.com.fiap.workshop_management_system.identity.auth.application.exception.InvalidCredentialsException;
import br.com.fiap.workshop_management_system.identity.auth.application.port.IssuedToken;
import br.com.fiap.workshop_management_system.identity.auth.application.port.PasswordHasher;
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticateUserAccountUseCaseTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final AuthenticateUserAccountUseCase useCase =
            new AuthenticateUserAccountUseCase(repository, passwordHasher, tokenIssuer);

    private final UserAccount account = UserAccount.create(
            new Username("jane.doe"), "$2a$10$hashvalue", Role.ADMIN, null, Instant.parse("2026-08-24T12:00:00Z"));

    @Test
    void issuesATokenWhenCredentialsAreValid() {
        when(repository.findByUsername(new Username("jane.doe"))).thenReturn(Optional.of(account));
        when(passwordHasher.matches("correct-password", account.passwordHash())).thenReturn(true);
        IssuedToken issuedToken = new IssuedToken("token-value", Role.ADMIN, Instant.parse("2026-08-24T13:00:00Z"));
        when(tokenIssuer.issue(account)).thenReturn(issuedToken);

        IssuedTokenResponse response = useCase.execute(new LoginRequest("jane.doe", "correct-password"));

        assertEquals("token-value", response.token());
        assertEquals(Role.ADMIN, response.role());
        assertEquals(issuedToken.expiresAt(), response.expiresAt());
    }

    @Test
    void rejectsAnUnknownUsernameWithTheSameExceptionAsAWrongPassword() {
        when(repository.findByUsername(new Username("unknown"))).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> useCase.execute(new LoginRequest("unknown", "any-password")));
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void rejectsAWrongPasswordWithTheSameExceptionAsAnUnknownUsername() {
        when(repository.findByUsername(new Username("jane.doe"))).thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrong-password", account.passwordHash())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> useCase.execute(new LoginRequest("jane.doe", "wrong-password")));
        verifyNoInteractions(tokenIssuer);
    }
}
