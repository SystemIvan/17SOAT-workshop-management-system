package br.com.fiap.workshop_management_system.identity.auth.infrastructure.security;

import br.com.fiap.workshop_management_system.identity.auth.application.exception.InvalidTokenException;
import br.com.fiap.workshop_management_system.identity.auth.application.port.IssuedToken;
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenClaims;
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 JWT issuance/parsing (ADR-003). The domain never sees this class — only the
 * {@link TokenIssuer} port it implements.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    static final Duration EXPIRATION = Duration.ofHours(1);
    private static final String ROLE_CLAIM = "role";
    private static final String LINKED_DOMAIN_ID_CLAIM = "linkedDomainId";

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public JwtTokenIssuer(@Value("${app.security.jwt.secret}") String secret) {
        this(secret, Clock.systemUTC());
    }

    JwtTokenIssuer(String secret, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(UserAccount account) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(EXPIRATION);
        String token = Jwts.builder()
                .subject(account.id().toString())
                .claim(ROLE_CLAIM, account.role().name())
                .claim(LINKED_DOMAIN_ID_CLAIM, stringOrNull(account.linkedDomainId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, account.role(), expiresAt);
    }

    @Override
    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userAccountId = UUID.fromString(claims.getSubject());
            Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
            String linkedDomainIdRaw = claims.get(LINKED_DOMAIN_ID_CLAIM, String.class);
            UUID linkedDomainId = linkedDomainIdRaw == null ? null : UUID.fromString(linkedDomainIdRaw);
            return new TokenClaims(userAccountId, role, linkedDomainId, claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException(exception);
        }
    }

    private static String stringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }
}
