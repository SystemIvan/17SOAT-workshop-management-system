package br.com.fiap.workshop_management_system.identity;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real HTTP test for the separate management port (container-readiness, R1/R2/R4). MockMvc cannot prove this:
 * it bypasses the embedded servers, so only a RANDOM_PORT context shows which port serves the probes and that
 * the security filter chain also guards the management child context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class ContainerProbesIntegrationTest {

    private static final List<String> PROBES =
            List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness");

    // Boot 4.1 always lists the group names on the primary health endpoint; no property hides them.
    private static final String AGGREGATE_UP = "{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}";
    private static final String AGGREGATE_DOWN = "{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"DOWN\"}";

    @LocalManagementPort
    private int managementPort;

    @LocalServerPort
    private int serverPort;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Test
    void probesAnswerUpWithStatusOnlyOnManagementPortWithoutToken() {
        for (String probe : List.of("/actuator/health/liveness", "/actuator/health/readiness")) {
            Reply reply = call(managementPort, HttpMethod.GET, probe, null);

            assertThat(reply.status()).as(probe).isEqualTo(200);
            assertThat(reply.body()).as(probe).isEqualTo("{\"status\":\"UP\"}");
        }

        Reply aggregate = call(managementPort, HttpMethod.GET, "/actuator/health", null);
        assertThat(aggregate.status()).isEqualTo(200);
        assertThat(aggregate.body()).isEqualTo(AGGREGATE_UP);
    }

    @Test
    void businessPortDoesNotServeHealth() {
        for (String probe : PROBES) {
            Reply reply = call(serverPort, HttpMethod.GET, probe, null);

            // No actuator endpoint is mapped on the business port; the request is rejected with 401 and
            // never reaches any health content.
            assertThat(reply.status()).as(probe).isEqualTo(401);
            assertThat(reply.body()).as(probe).doesNotContain("UP");
        }
    }

    @Test
    void otherManagementPathsAreDeniedToAnonymousCallers() {
        for (String path : List.of("/actuator", "/actuator/env", "/actuator/beans", "/actuator/health/db")) {
            Reply reply = call(managementPort, HttpMethod.GET, path, null);

            assertThat(reply.status()).as(path).isEqualTo(401);
            assertThat(reply.body()).as(path).doesNotContain("UP");
        }
    }

    @Test
    void writeMethodsOnProbesAreDenied() {
        for (String probe : PROBES) {
            for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
                Reply reply = call(managementPort, method, probe, null);

                assertThat(reply.status()).as(method + " " + probe).isEqualTo(401);
            }
        }
    }

    @Test
    void invalidBearerTokenDoesNotAffectProbes() {
        Reply reply = call(managementPort, HttpMethod.GET, "/actuator/health/liveness", "Bearer lixo");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void validAdminTokenGrantsNothingBeyondProbes() {
        String adminToken = "Bearer " + tokenFor(Role.ADMIN);

        // The actuator chain has no JWT filter, so even a valid ADMIN token is treated as anonymous here.
        assertThat(call(managementPort, HttpMethod.GET, "/actuator/env", adminToken).status()).isEqualTo(401);
        assertThat(call(managementPort, HttpMethod.GET, "/actuator/health/liveness", adminToken).status())
                .isEqualTo(200);
    }

    private String tokenFor(Role role) {
        UserAccount account = UserAccount.create(
                new Username("probe." + UUID.randomUUID()), "$2a$10$hashvalue", role, null, Instant.now());
        return tokenIssuer.issue(account).token();
    }

    /**
     * Overrides the auto-configured {@code db} contributor (same bean name, so Boot's
     * {@code @ConditionalOnMissingBean} backs off) with the real {@link DataSourceHealthIndicator} pointed at a
     * DataSource that cannot connect. The application's own DataSource stays healthy for Flyway and JPA.
     */
    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "management.server.port=0")
    @Import(DatabaseDown.UnreachableDatabaseHealthConfiguration.class)
    class DatabaseDown {

        @LocalManagementPort
        private int downManagementPort;

        @Test
        void readinessAndAggregateGoDownWhileLivenessStaysUp() {
            Reply liveness = call(downManagementPort, HttpMethod.GET, "/actuator/health/liveness", null);
            Reply readiness = call(downManagementPort, HttpMethod.GET, "/actuator/health/readiness", null);
            Reply health = call(downManagementPort, HttpMethod.GET, "/actuator/health", null);

            assertThat(liveness.status()).isEqualTo(200);
            assertThat(liveness.body()).isEqualTo("{\"status\":\"UP\"}");
            assertThat(readiness.status()).isEqualTo(503);
            assertThat(readiness.body()).isEqualTo("{\"status\":\"DOWN\"}");
            assertThat(health.status()).isEqualTo(503);
            assertThat(health.body()).isEqualTo(AGGREGATE_DOWN);
        }

        @TestConfiguration
        static class UnreachableDatabaseHealthConfiguration {

            @Bean
            DataSourceHealthIndicator dbHealthContributor() throws SQLException {
                DataSource unreachable = mock(DataSource.class);
                when(unreachable.getConnection()).thenThrow(new SQLException("database unreachable"));
                return new DataSourceHealthIndicator(unreachable);
            }
        }
    }

    private static Reply call(int port, HttpMethod method, String path, String authorization) {
        return RestClient.create("http://localhost:" + port)
                .method(method)
                .uri(path)
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .exchange((request, response) -> new Reply(
                        response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private record Reply(int status, String body) {
    }
}
