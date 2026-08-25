package br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogServiceNameTest {

    @Test
    void trimsExternalSpacesAndCreatesCaseInsensitiveCanonicalValue() {
        CatalogServiceName name = new CatalogServiceName("  Troca de Óleo  ");

        assertThat(name.value()).isEqualTo("Troca de Óleo");
        assertThat(name.canonicalValue()).isEqualTo("troca de óleo");
    }

    @Test
    void preservesAccentsAndInternalSpaces() {
        CatalogServiceName accented = new CatalogServiceName("Revisão elétrica");
        CatalogServiceName withoutAccent = new CatalogServiceName("Revisao elétrica");
        CatalogServiceName doubleSpace = new CatalogServiceName("Revisão  elétrica");

        assertThat(accented.canonicalValue()).isNotEqualTo(withoutAccent.canonicalValue());
        assertThat(accented.canonicalValue()).isNotEqualTo(doubleSpace.canonicalValue());
    }

    @Test
    void rejectsMissingBlankAndOversizedNames() {
        assertThatThrownBy(() -> new CatalogServiceName(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogServiceName("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogServiceName("a".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
