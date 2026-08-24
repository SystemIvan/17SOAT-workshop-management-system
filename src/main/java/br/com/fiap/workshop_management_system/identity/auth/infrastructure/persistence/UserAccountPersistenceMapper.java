package br.com.fiap.workshop_management_system.identity.auth.infrastructure.persistence;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import org.springframework.stereotype.Component;

/**
 * Converte o agregado {@link UserAccount}, independente de framework, e sua projeção JPA.
 * A reconstrução do objeto de domínio usa {@link UserAccount#reconstitute}, restaurando
 * exatamente o estado persistido sem executar novamente as regras de criação.
 */
@Component
public class UserAccountPersistenceMapper {

    public UserAccountJpaEntity toEntity(UserAccount account) {
        return new UserAccountJpaEntity(
                account.id(),
                account.username().value(),
                account.passwordHash(),
                account.role(),
                account.linkedDomainId(),
                account.createdAt());
    }

    public UserAccount toDomain(UserAccountJpaEntity entity) {
        return UserAccount.reconstitute(
                entity.getId(),
                new Username(entity.getUsername()),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getLinkedDomainId(),
                entity.getCreatedAt());
    }
}
