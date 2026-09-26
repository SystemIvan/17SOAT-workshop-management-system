package br.com.fiap.workshop_management_system.identity;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstimateGatewayHmacAuthenticationFilterTest {

    private static final String SECRET = "unit-test-estimate-gateway-secret";
    private static final long TOLERANCE_SECONDS = 300;
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final String DECISIONS_PATH = "/api/estimates/3f1c2d4e-0000-0000-0000-000000000001/decisions";
    private static final String BODY =
            "{\"decisions\":[{\"serviceExecutionId\":\"3f1c2d4e-0000-0000-0000-000000000002\","
            + "\"decision\":\"APPROVED\"}]}";

    private static final int MAX_BODY_BYTES = 1024;

    private final EstimateGatewayHmacAuthenticationFilter filter = new EstimateGatewayHmacAuthenticationFilter(
            SECRET, TOLERANCE_SECONDS, MAX_BODY_BYTES, new ApiAuthenticationEntryPoint(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validSignatureWithinWindowAuthenticatesTheGateway() throws Exception {
        String timestamp = epochSeconds(NOW);
        MockHttpServletRequest request = signedRequest(timestamp, sign(timestamp, BODY));
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = chain.authentication;
        assertNotNull(authentication);
        assertEquals(EstimateGatewayHmacAuthenticationFilter.GATEWAY_PRINCIPAL, authentication.getPrincipal());
        assertEquals(
                List.of(new SimpleGrantedAuthority(EstimateGatewayHmacAuthenticationFilter.GATEWAY_AUTHORITY)),
                List.copyOf(authentication.getAuthorities()));
    }

    @Test
    void uppercaseHexSignatureIsAccepted() throws Exception {
        String timestamp = epochSeconds(NOW);
        MockHttpServletRequest request = signedRequest(timestamp, sign(timestamp, BODY).toUpperCase());
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.authentication);
    }

    @Test
    void timestampAtTheEdgeOfTheWindowIsAccepted() throws Exception {
        String timestamp = epochSeconds(NOW.minusSeconds(TOLERANCE_SECONDS));
        CapturingChain chain = new CapturingChain();

        filter.doFilter(signedRequest(timestamp, sign(timestamp, BODY)), new MockHttpServletResponse(), chain);

        assertNotNull(chain.authentication);
    }

    @Test
    void missingHeadersLeaveTheRequestUnauthenticatedAndContinueTheChain() throws Exception {
        MockHttpServletRequest request = decisionsRequest();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, chain.request, "unsigned calls must reach the JWT filter with the body unread");
        assertNull(chain.authentication);
    }

    @Test
    void unsignedCallWithAHugeBodyIsNotReadNorRejectedHere() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", DECISIONS_PATH);
        request.setContent(new byte[MAX_BODY_BYTES * 4]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.request);
        assertEquals(200, response.getStatus());
    }

    @Test
    void signedBodyOverTheLimitIsRejectedWith401WithoutCallingTheChain() throws Exception {
        String body = "x".repeat(MAX_BODY_BYTES + 1);
        String timestamp = epochSeconds(NOW);
        MockHttpServletRequest request = decisionsRequest();
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.TIMESTAMP_HEADER, timestamp);
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.SIGNATURE_HEADER, sign(timestamp, body));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("UNAUTHORIZED", JsonPath.read(response.getContentAsString(), "$.code"));
        assertNull(chain.request);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void declaredContentLengthOverTheLimitIsRejectedBeforeReading() throws Exception {
        String timestamp = epochSeconds(NOW);
        MockHttpServletRequest declaringRequest = new MockHttpServletRequest("POST", DECISIONS_PATH) {
            @Override
            public long getContentLengthLong() {
                return MAX_BODY_BYTES + 1L;
            }

            @Override
            public ServletInputStream getInputStream() {
                throw new AssertionError("body must not be read when Content-Length is over the limit");
            }
        };
        declaringRequest.addHeader(EstimateGatewayHmacAuthenticationFilter.TIMESTAMP_HEADER, timestamp);
        declaringRequest.addHeader(EstimateGatewayHmacAuthenticationFilter.SIGNATURE_HEADER, sign(timestamp, BODY));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(declaringRequest, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.request);
    }

    @Test
    void signedBodyExactlyAtTheLimitIsAuthenticated() throws Exception {
        String body = "x".repeat(MAX_BODY_BYTES);
        String timestamp = epochSeconds(NOW);
        MockHttpServletRequest request = decisionsRequest();
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.TIMESTAMP_HEADER, timestamp);
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.SIGNATURE_HEADER, sign(timestamp, body));
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.authentication);
    }

    @Test
    void onlyOneHeaderPresentLeavesTheRequestUnauthenticated() throws Exception {
        MockHttpServletRequest request = decisionsRequest();
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.SIGNATURE_HEADER, sign(epochSeconds(NOW), BODY));
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(chain.authentication);
    }

    @Test
    void wrongSignatureLeavesTheRequestUnauthenticated() throws Exception {
        String timestamp = epochSeconds(NOW);
        String signedWithOtherSecret = hmac("another-secret", timestamp + "." + BODY);
        CapturingChain chain = new CapturingChain();

        filter.doFilter(signedRequest(timestamp, signedWithOtherSecret), new MockHttpServletResponse(), chain);

        assertNull(chain.authentication);
    }

    @Test
    void tamperedBodyLeavesTheRequestUnauthenticated() throws Exception {
        String timestamp = epochSeconds(NOW);
        MockHttpServletRequest request = signedRequest(timestamp, sign(timestamp, BODY));
        request.setContent(BODY.replace("APPROVED", "REJECTED").getBytes(StandardCharsets.UTF_8));
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(chain.authentication);
    }

    @Test
    void expiredTimestampIsRejectedEvenWithACorrectSignature() throws Exception {
        String timestamp = epochSeconds(NOW.minusSeconds(TOLERANCE_SECONDS + 1));
        CapturingChain chain = new CapturingChain();

        filter.doFilter(signedRequest(timestamp, sign(timestamp, BODY)), new MockHttpServletResponse(), chain);

        assertNull(chain.authentication);
    }

    @Test
    void futureTimestampBeyondTheWindowIsRejected() throws Exception {
        String timestamp = epochSeconds(NOW.plusSeconds(TOLERANCE_SECONDS + 1));
        CapturingChain chain = new CapturingChain();

        filter.doFilter(signedRequest(timestamp, sign(timestamp, BODY)), new MockHttpServletResponse(), chain);

        assertNull(chain.authentication);
    }

    @Test
    void extremeTimestampDoesNotOverflowIntoTheWindow() throws Exception {
        String timestamp = String.valueOf(Long.MIN_VALUE);
        CapturingChain chain = new CapturingChain();

        filter.doFilter(signedRequest(timestamp, sign(timestamp, BODY)), new MockHttpServletResponse(), chain);

        assertNull(chain.authentication);
    }

    @Test
    void malformedTimestampLeavesTheRequestUnauthenticatedWithoutThrowing() throws Exception {
        CapturingChain chain = new CapturingChain();

        MockHttpServletRequest request = signedRequest("not-a-number", sign("not-a-number", BODY));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(chain.authentication);
    }

    @Test
    void invalidSignatureClearsAPreviouslyPopulatedContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null, List.of()));
        String timestamp = epochSeconds(NOW);
        CapturingChain chain = new CapturingChain();

        filter.doFilter(signedRequest(timestamp, "deadbeef"), new MockHttpServletResponse(), chain);

        assertNull(chain.authentication);
    }

    @Test
    void otherPathsAreNotWrappedNorAuthenticated() throws Exception {
        String timestamp = epochSeconds(NOW);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/service-orders");
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.TIMESTAMP_HEADER, timestamp);
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.SIGNATURE_HEADER, sign(timestamp, BODY));
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, chain.request);
        assertNull(chain.authentication);
    }

    @Test
    void otherMethodsOnTheDecisionsPathAreNotWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", DECISIONS_PATH);
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, chain.request);
    }

    @Test
    void bodyIsStillReadableDownstreamAfterSignatureVerification() throws Exception {
        String timestamp = epochSeconds(NOW);
        CapturingChain chain = new CapturingChain();

        filter.doFilter(signedRequest(timestamp, sign(timestamp, BODY)), new MockHttpServletResponse(), chain);

        assertInstanceOf(CachedBodyHttpServletRequest.class, chain.request);
        assertArrayEquals(BODY.getBytes(StandardCharsets.UTF_8), chain.request.getInputStream().readAllBytes());
    }

    @Test
    void blankSecretIsRejectedAtStartup() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint();

        assertThrows(IllegalArgumentException.class, () -> new EstimateGatewayHmacAuthenticationFilter(
                " ", TOLERANCE_SECONDS, MAX_BODY_BYTES, entryPoint, clock));
    }

    @Test
    void nonPositiveBodyLimitIsRejectedAtStartup() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint();

        assertThrows(IllegalArgumentException.class, () -> new EstimateGatewayHmacAuthenticationFilter(
                SECRET, TOLERANCE_SECONDS, 0, entryPoint, clock));
    }

    private static MockHttpServletRequest decisionsRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", DECISIONS_PATH);
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static MockHttpServletRequest signedRequest(String timestamp, String signature) {
        MockHttpServletRequest request = decisionsRequest();
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.TIMESTAMP_HEADER, timestamp);
        request.addHeader(EstimateGatewayHmacAuthenticationFilter.SIGNATURE_HEADER, signature);
        return request;
    }

    private static String epochSeconds(Instant instant) {
        return String.valueOf(instant.getEpochSecond());
    }

    private static String sign(String timestamp, String body) throws Exception {
        return hmac(SECRET, timestamp + "." + body);
    }

    private static String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    /** Records what the next filter in the chain would observe. */
    private static final class CapturingChain extends MockFilterChain {

        private ServletRequest request;
        private Authentication authentication;

        @Override
        public void doFilter(ServletRequest request, jakarta.servlet.ServletResponse response) {
            this.request = request;
            this.authentication = SecurityContextHolder.getContext().getAuthentication();
        }
    }
}
