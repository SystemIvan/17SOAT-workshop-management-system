package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxIdTest {

    @Test
    void normalizesAndValidatesCpf() {
        TaxId taxId = new TaxId(" 529.982.247-25 ");

        assertEquals("52998224725", taxId.value());
        assertEquals(TaxId.Type.CPF, taxId.type());
        assertEquals(new TaxId("52998224725"), taxId);
    }

    @Test
    void normalizesAndValidatesCnpj() {
        TaxId taxId = new TaxId("11.222.333/0001-81");

        assertEquals("11222333000181", taxId.value());
        assertEquals(TaxId.Type.CNPJ, taxId.type());
    }

    @Test
    void rejectsBlankMalformedAndUnsupportedValues() {
        assertThrows(IllegalArgumentException.class, () -> new TaxId(null));
        assertThrows(IllegalArgumentException.class, () -> new TaxId(" "));
        assertThrows(IllegalArgumentException.class, () -> new TaxId("123456789"));
        assertThrows(IllegalArgumentException.class, () -> new TaxId("529.982.247-2A"));
        assertThrows(IllegalArgumentException.class, () -> new TaxId("5-2998224725"));
    }

    @Test
    void rejectsRepeatedDigitsAndInvalidCheckDigits() {
        assertThrows(IllegalArgumentException.class, () -> new TaxId("000.000.000-00"));
        assertThrows(IllegalArgumentException.class, () -> new TaxId("11.111.111/1111-11"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new TaxId("529.982.247-24"));
        assertEquals("O CPF/CNPJ do cliente possui dígitos verificadores inválidos", exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new TaxId("11.222.333/0001-80"));
    }
}
