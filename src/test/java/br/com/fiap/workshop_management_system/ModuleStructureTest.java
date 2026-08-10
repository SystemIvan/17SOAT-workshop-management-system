package br.com.fiap.workshop_management_system;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleStructureTest {

    static final ApplicationModules modules = ApplicationModules.of(WorkshopManagementSystemApplication.class);

    @Test
    void verifyModuleStructure() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
