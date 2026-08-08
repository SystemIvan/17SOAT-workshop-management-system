package br.com.fiap.workshop_management_system.application.customer.dto;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String document,
        ContactInfoDTO contactInfo) {
}
