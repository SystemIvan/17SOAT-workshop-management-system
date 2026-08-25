package br.com.fiap.workshop_management_system.identity.auth.application.port;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;

/**
 * Isolates JWT issuance/parsing from the domain model (AGENTS.md — keep the domain free from framework
 * concerns). Implemented in infrastructure with a real JWT library.
 */
public interface TokenIssuer {

    IssuedToken issue(UserAccount account);

    TokenClaims parse(String token);
}
