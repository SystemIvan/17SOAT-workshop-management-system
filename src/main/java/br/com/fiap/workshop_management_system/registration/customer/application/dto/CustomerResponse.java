package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String document,
        ContactInfoDTO contactInfo) {
}
