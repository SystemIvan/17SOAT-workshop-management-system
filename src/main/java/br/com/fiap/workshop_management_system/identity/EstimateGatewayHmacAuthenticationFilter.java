package br.com.fiap.workshop_management_system.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Authenticates the external estimate-approval gateway on {@code POST /api/estimates/{id}/decisions} through an
 * HMAC-SHA256 signature over {@code timestamp + "." + rawBody} (RF41, technical-spec.md —
 * estimate-decisions-external-auth).
 *
 * <p>The granted authority is synthetic: the gateway is not a domain actor, so it never goes through the
 * {@code Role} to domain-ID mapping of AD-016. A missing or invalid signature leaves the request
 * unauthenticated instead of failing here, so the JWT filter that follows can still authenticate internal users
 * and the authorization stage answers 401 through {@link ApiAuthenticationEntryPoint} otherwise.
 *
 * <p>The only case answered here is a signed call whose body exceeds {@code max-body-bytes}: the body is read
 * before authentication, so it is capped, and once partially consumed it cannot continue down the chain.
 */
@Component
public class EstimateGatewayHmacAuthenticationFilter extends OncePerRequestFilter {

    static final String TIMESTAMP_HEADER = "X-Estimate-Gateway-Timestamp";
    static final String SIGNATURE_HEADER = "X-Estimate-Gateway-Signature";
    static final String GATEWAY_AUTHORITY = "ESTIMATE_APPROVAL_GATEWAY";
    static final String GATEWAY_PRINCIPAL = "estimate-approval-gateway";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PROTECTED_PATH = "/api/estimates/*/decisions";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SecretKeySpec key;
    private final long toleranceSeconds;
    private final int maxBodyBytes;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final Clock clock;

    @Autowired
    public EstimateGatewayHmacAuthenticationFilter(
            @Value("${app.security.estimate-gateway.hmac-secret}") String secret,
            @Value("${app.security.estimate-gateway.timestamp-tolerance-seconds:300}") long toleranceSeconds,
            @Value("${app.security.estimate-gateway.max-body-bytes:65536}") int maxBodyBytes,
            ApiAuthenticationEntryPoint authenticationEntryPoint) {
        this(secret, toleranceSeconds, maxBodyBytes, authenticationEntryPoint, Clock.systemUTC());
    }

    EstimateGatewayHmacAuthenticationFilter(
            String secret, long toleranceSeconds, int maxBodyBytes,
            AuthenticationEntryPoint authenticationEntryPoint, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.security.estimate-gateway.hmac-secret must be configured");
        }
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("app.security.estimate-gateway.max-body-bytes must be positive");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.toleranceSeconds = toleranceSeconds;
        this.maxBodyBytes = maxBodyBytes;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isProtectedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);
        if (timestamp == null || signature == null) {
            // Not a gateway call: leave the body unread so anonymous requests cost nothing here and JWT callers
            // go through exactly as before RF41.
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }
        CachedBodyHttpServletRequest cachedRequest;
        try {
            cachedRequest = readBounded(request);
        } catch (CachedBodyHttpServletRequest.BodyTooLargeException tooLarge) {
            // The body is already partially consumed and cannot be handed downstream, so reject here with the
            // same generic 401 the authorization stage would produce for any other gateway failure.
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Gateway request body too large"));
            return;
        }
        if (isValid(timestamp, signature, cachedRequest.body())) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    GATEWAY_PRINCIPAL, null, List.of(new SimpleGrantedAuthority(GATEWAY_AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(cachedRequest, response);
    }

    private CachedBodyHttpServletRequest readBounded(HttpServletRequest request) throws IOException {
        // A declared Content-Length over the limit is rejected before reading a single byte.
        if (request.getContentLengthLong() > maxBodyBytes) {
            throw new CachedBodyHttpServletRequest.BodyTooLargeException(maxBodyBytes);
        }
        return new CachedBodyHttpServletRequest(request, maxBodyBytes);
    }

    private boolean isProtectedRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return HttpMethod.POST.matches(request.getMethod()) && PATH_MATCHER.match(PROTECTED_PATH, path);
    }

    private boolean isValid(String timestamp, String signature, byte[] body) {
        long signedAtEpochSeconds;
        try {
            signedAtEpochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException malformedTimestamp) {
            return false;
        }
        // The signature is checked before the window so both failures cost the same work; neither the
        // response nor the logs reveal which check failed.
        boolean signatureMatches = MessageDigest.isEqual(
                expectedSignature(timestamp.trim(), body).getBytes(StandardCharsets.US_ASCII),
                signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        return signatureMatches && isWithinWindow(signedAtEpochSeconds);
    }

    private boolean isWithinWindow(long signedAtEpochSeconds) {
        // Bounds instead of Math.abs(now - signedAt): a timestamp near Long.MIN_VALUE would overflow the
        // subtraction into a negative distance and pass the check.
        long now = clock.instant().getEpochSecond();
        return signedAtEpochSeconds >= now - toleranceSeconds && signedAtEpochSeconds <= now + toleranceSeconds;
    }

    private String expectedSignature(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException unavailable) {
            // HmacSHA256 is mandatory on every JDK; reaching this is a platform defect, not a client error.
            throw new IllegalStateException("HmacSHA256 is not available", unavailable);
        }
    }
}
