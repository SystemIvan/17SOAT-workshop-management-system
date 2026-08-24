package br.com.fiap.workshop_management_system.identity.auth.application.usecase;

import br.com.fiap.workshop_management_system.identity.auth.application.dto.CreateUserAccountRequest;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.UserAccountMapper;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.UserAccountResponse;
import br.com.fiap.workshop_management_system.identity.auth.application.exception.DuplicateUsernameException;
import br.com.fiap.workshop_management_system.identity.auth.application.port.PasswordHasher;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Reachable only by an already-authenticated ADMIN — enforced by the HTTP authorization layer
 * (SecurityConfig), not by this use case (AGENTS.md keeps authorization at the boundary it belongs to).
 */
@Service
public class CreateUserAccountUseCase {

    private final UserAccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Autowired
    public CreateUserAccountUseCase(UserAccountRepository repository, PasswordHasher passwordHasher) {
        this(repository, passwordHasher, Clock.systemUTC());
    }

    CreateUserAccountUseCase(UserAccountRepository repository, PasswordHasher passwordHasher, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Transactional
    public UserAccountResponse execute(CreateUserAccountRequest request) {
        Username username = new Username(request.username());
        if (repository.existsByUsername(username)) {
            throw new DuplicateUsernameException();
        }
        String passwordHash = passwordHasher.hash(request.password());
        UserAccount account = UserAccount.create(
                username, passwordHash, request.role(), request.linkedDomainId(), clock.instant());
        repository.save(account);
        return UserAccountMapper.toResponse(account);
    }
}
