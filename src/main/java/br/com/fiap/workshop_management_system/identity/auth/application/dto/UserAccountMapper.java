package br.com.fiap.workshop_management_system.identity.auth.application.dto;

import br.com.fiap.workshop_management_system.identity.auth.application.port.IssuedToken;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;

/**
 * Converte o agregado UserAccount e os DTOs da camada de aplicação.
 * Entidades nunca atravessam diretamente a fronteira do controller.
 */
public final class UserAccountMapper {

    private UserAccountMapper() {
    }

    public static UserAccountResponse toResponse(UserAccount account) {
        return new UserAccountResponse(
                account.id(), account.username().value(), account.role(), account.linkedDomainId());
    }

    public static IssuedTokenResponse toIssuedTokenResponse(IssuedToken issuedToken) {
        return new IssuedTokenResponse(issuedToken.token(), issuedToken.role(), issuedToken.expiresAt());
    }
}
