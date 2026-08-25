package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibilityPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
class CatalogServiceEligibilityApplicationModuleTest {

    @Autowired
    private CatalogServiceEligibilityPort eligibilityPort;

    @Test
    void resolvesTheCatalogServiceEligibilityAdapterAcrossModules() {
        assertThat(eligibilityPort).isNotNull();
    }
}
