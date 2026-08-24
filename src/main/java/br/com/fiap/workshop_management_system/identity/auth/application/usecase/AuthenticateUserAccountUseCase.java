package br.com.fiap.workshop_management_system.identity.auth.application.usecase;

import br.com.fiap.workshop_management_system.identity.auth.application.dto.IssuedTokenResponse;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.LoginRequest;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.UserAccountMapper;
import br.com.fiap.workshop_management_system.identity.auth.application.exception.InvalidCredentialsException;
import br.com.fiap.workshop_management_system.identity.auth.application.port.PasswordHasher;
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticateUserAccountUseCase {

    private final UserAccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    public AuthenticateUserAccountUseCase(
            UserAccountRepository repository, PasswordHasher passwordHasher, TokenIssuer tokenIssuer) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional(readOnly = true)
    public IssuedTokenResponse execute(LoginRequest request) {
        UserAccount account = repository.findByUsername(new Username(request.username()))
                .filter(found -> passwordHasher.matches(request.password(), found.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return UserAccountMapper.toIssuedTokenResponse(tokenIssuer.issue(account));
    }
}
