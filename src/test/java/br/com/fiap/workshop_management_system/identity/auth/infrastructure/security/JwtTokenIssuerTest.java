package br.com.fiap.workshop_management_system.identity.auth.infrastructure.security;

import br.com.fiap.workshop_management_system.identity.auth.application.exception.InvalidTokenException;
import br.com.fiap.workshop_management_system.identity.auth.application.port.IssuedToken;
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenClaims;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenIssuerTest {

    private static final String SECRET = "unit-test-secret-with-at-least-32-bytes-of-length";
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    private final JwtTokenIssuer issuer = new JwtTokenIssuer(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void roundTripsIssueAndParseForAnAccountLinkedToADomainAggregate() {
        UUID linkedDomainId = UUID.randomUUID();
        UserAccount account = UserAccount.create(
                new Username("john.tech"), "$2a$10$hashvalue", Role.TECHNICIAN, linkedDomainId, NOW);

        IssuedToken issuedToken = issuer.issue(account);
        TokenClaims claims = issuer.parse(issuedToken.token());

        assertEquals(account.id(), claims.userAccountId());
        assertEquals(Role.TECHNICIAN, claims.role());
        assertEquals(linkedDomainId, claims.linkedDomainId());
        assertEquals(NOW.plus(JwtTokenIssuer.EXPIRATION), claims.expiresAt());
        assertEquals(NOW.plus(JwtTokenIssuer.EXPIRATION), issuedToken.expiresAt());
    }

    @Test
    void roundTripsIssueAndParseForAnAccountWithoutALinkedDomainId() {
        UserAccount account = UserAccount.create(new Username("admin"), "$2a$10$hashvalue", Role.ADMIN, null, NOW);

        TokenClaims claims = issuer.parse(issuer.issue(account).token());

        assertNull(claims.linkedDomainId());
    }

    @Test
    void rejectsAnExpiredToken() {
        UserAccount account = UserAccount.create(new Username("admin"), "$2a$10$hashvalue", Role.ADMIN, null, NOW);
        String token = issuer.issue(account).token();

        Clock later = Clock.fixed(NOW.plus(JwtTokenIssuer.EXPIRATION).plusSeconds(1), ZoneOffset.UTC);
        JwtTokenIssuer laterIssuer = new JwtTokenIssuer(SECRET, later);

        assertThrows(InvalidTokenException.class, () -> laterIssuer.parse(token));
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        UserAccount account = UserAccount.create(new Username("admin"), "$2a$10$hashvalue", Role.ADMIN, null, NOW);
        String token = issuer.issue(account).token();
        JwtTokenIssuer otherIssuer = new JwtTokenIssuer(
                "a-completely-different-secret-with-at-least-32-bytes", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(InvalidTokenException.class, () -> otherIssuer.parse(token));
    }

    @Test
    void rejectsAMalformedToken() {
        assertThrows(InvalidTokenException.class, () -> issuer.parse("not-a-jwt"));
    }
}
