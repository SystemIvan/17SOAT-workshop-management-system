package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CatalogServiceRepositoryIntegrationTest {

    @Autowired
    private CatalogServiceRepository repository;

    @Autowired
    private CatalogServiceJpaRepository jpaRepository;

    @Test
    void persistsAndRestoresCatalogService() {
        CatalogService catalogService = service("Alinhamento " + UUID.randomUUID(), "149.90", true);

        repository.save(catalogService);

        CatalogServiceJpaEntity stored = jpaRepository.findById(catalogService.id()).orElseThrow();
        assertEquals(catalogService.name().value(), stored.getName());
        assertArrayEquals(catalogService.name().canonicalValue().getBytes(StandardCharsets.UTF_8),
                stored.getNormalizedNameKey());
        assertEquals(new BigDecimal("149.90"), stored.getBasePriceValue());
        assertEquals("BRL", stored.getBasePriceCurrency());
        assertTrue(stored.isActive());

        CatalogService restored = repository.findById(catalogService.id()).orElseThrow();
        assertEquals(catalogService.id(), restored.id());
        assertEquals(catalogService.name(), restored.name());
        assertEquals(catalogService.basePrice(), restored.basePrice());
        assertTrue(restored.active());
    }

    @Test
    void findsByCanonicalNameIgnoringCaseAndExternalWhitespace() {
        String suffix = UUID.randomUUID().toString();
        CatalogService catalogService = service("Troca de Óleo " + suffix, "89.00", true);
        repository.save(catalogService);

        CatalogService found = repository.findByName(
                new CatalogServiceName("  TROCA DE ÓLEO " + suffix.toUpperCase() + "  ")).orElseThrow();

        assertEquals(catalogService.id(), found.id());
        assertEquals(catalogService.name().value(), found.name().value());
    }

    @Test
    void listsOnlyActiveCatalogServices() {
        CatalogService active = service("Ativo " + UUID.randomUUID(), "10.00", true);
        CatalogService inactive = service("Inativo " + UUID.randomUUID(), "20.00", false);
        repository.save(active);
        repository.save(inactive);

        assertTrue(repository.findAllActive().stream().anyMatch(service -> service.id().equals(active.id())));
        assertFalse(repository.findAllActive().stream().anyMatch(service -> service.id().equals(inactive.id())));
    }

    @Test
    void translatesNamedDatabaseConstraintUsingPersistedIdentity() {
        String name = "Balanceamento " + UUID.randomUUID();
        CatalogService existing = service(name, "60.00", true);
        repository.save(existing);

        CatalogServiceNameAlreadyExistsException exception = assertThrows(
                CatalogServiceNameAlreadyExistsException.class,
                () -> repository.save(service(name.toUpperCase(), "70.00", true)));

        assertEquals(existing.id(), exception.existingId());
        assertEquals(existing.name().value(), exception.existingName());
        assertEquals("Já existe um serviço cadastrado com esse nome: "
                + existing.id() + " - " + existing.name().value(), exception.getMessage());
    }

    private static CatalogService service(String name, String value, boolean active) {
        return CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName(name),
                new Money(new BigDecimal(value), CurrencyCode.BRL),
                active);
    }
}
