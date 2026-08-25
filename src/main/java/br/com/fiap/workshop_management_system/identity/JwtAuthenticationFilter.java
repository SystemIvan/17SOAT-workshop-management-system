package br.com.fiap.workshop_management_system.identity;

import br.com.fiap.workshop_management_system.identity.auth.application.exception.InvalidTokenException;
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenClaims;
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Populates the SecurityContext from a Bearer JWT. Only consumes the public
 * {@link TokenIssuer} port of the identity module — never its internal packages.
 *
 * <p>{@code @ConditionalOnBean(TokenIssuer.class)}: defensive guard in case some future test slice
 * loads this class without the rest of the {@code identity} module wired.
 */
@Component
@ConditionalOnBean(TokenIssuer.class)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenIssuer tokenIssuer;

    public JwtAuthenticationFilter(TokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                TokenClaims claims = tokenIssuer.parse(header.substring(BEARER_PREFIX.length()));
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(claims.role().name()));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(claims.userAccountId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException invalidToken) {
                // A missing/invalid/expired token is treated as unauthenticated, not a hard error here:
                // the authorization stage rejects the request with 401 via ApiAuthenticationEntryPoint,
                // the same outcome as sending no token at all.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
