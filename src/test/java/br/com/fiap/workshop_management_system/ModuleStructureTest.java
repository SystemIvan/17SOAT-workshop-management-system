package br.com.fiap.workshop_management_system;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleStructureTest {

    static final ApplicationModules modules = ApplicationModules.of(WorkshopManagementSystemApplication.class);

    @Test
    void verifyModuleStructure() {
        modules.verify();

        assertThat(modules.stream().map(module -> module.getIdentifier().toString()).toList())
                .containsExactlyInAnyOrder("registration", "servicelifecycle", "stockprocurement");
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
