package br.com.fiap.workshop_management_system.testsupport;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;

import java.time.Instant;
import java.util.UUID;

/**
 * Mints a JWT for MockMvcTest suites that need to pass the security layer but are not themselves
 * testing authorization (that is {@code SecurityAuthorizationTest}'s job). ADMIN is authorized on
 * every endpoint in the matrix (technical-spec.md — jwt-authentication), so it is the uniform choice
 * here; no UserAccount is persisted, since JwtAuthenticationFilter trusts token claims directly.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static String adminToken(TokenIssuer tokenIssuer) {
        UserAccount account = UserAccount.create(
                new Username("test-admin." + UUID.randomUUID()), "$2a$10$hashvalue", Role.ADMIN, null,
                Instant.now());
        return tokenIssuer.issue(account).token();
    }
}
