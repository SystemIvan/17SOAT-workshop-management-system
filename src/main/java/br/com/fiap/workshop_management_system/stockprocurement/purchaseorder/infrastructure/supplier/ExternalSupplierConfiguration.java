package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.supplier;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Set;

@Configuration
@EnableConfigurationProperties(ExternalSupplierProperties.class)
class ExternalSupplierConfiguration {

    private static final Set<String> ALLOWED_SIMULATOR_HOSTS = Set.of(
            "localhost", "127.0.0.1", "supplier-simulator");

    @Bean
    RestClient externalSupplierRestClient(ExternalSupplierProperties properties) {
        validateSimulatorEndpoint(properties);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    private void validateSimulatorEndpoint(ExternalSupplierProperties properties) {
        String host = properties.baseUrl().getHost();
        if (!"http".equals(properties.baseUrl().getScheme()) || !ALLOWED_SIMULATOR_HOSTS.contains(host)) {
            throw new IllegalStateException(
                    "RF27 only permits the local External Supplier System simulator");
        }
    }
}
