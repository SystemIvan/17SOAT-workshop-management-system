package br.com.fiap.workshop_management_system.identity.auth.domain.repository;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByUsername(Username username);

    boolean existsByUsername(Username username);

    void save(UserAccount account);
}
