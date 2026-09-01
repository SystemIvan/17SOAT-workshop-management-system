package br.com.fiap.workshop_management_system.identity.auth.infrastructure.persistence;

import br.com.fiap.workshop_management_system.identity.auth.application.exception.DuplicateUsernameException;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de infraestrutura da porta {@link UserAccountRepository}, implementado com JPA.
 */
@Repository
public class UserAccountRepositoryImpl implements UserAccountRepository {

    private final UserAccountJpaRepository jpaRepository;
    private final UserAccountPersistenceMapper mapper;

    public UserAccountRepositoryImpl(UserAccountJpaRepository jpaRepository, UserAccountPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<UserAccount> findByUsername(Username username) {
        return jpaRepository.findByUsername(username.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(Username username) {
        return jpaRepository.existsByUsername(username.value());
    }

    @Override
    public void save(UserAccount account) {
        try {
            jpaRepository.saveAndFlush(mapper.toEntity(account));
        } catch (DataIntegrityViolationException exception) {
            if (isUsernameUniquenessViolation(exception)) {
                throw new DuplicateUsernameException();
            }
            throw exception;
        }
    }

    private static boolean isUsernameUniquenessViolation(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("uk_user_accounts_username");
    }
}
