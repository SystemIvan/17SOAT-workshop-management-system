package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        @Schema(description = "CPF ou CNPJ normalizado contendo apenas dígitos", example = "52998224725")
        String document,
        ContactInfoDTO contactInfo,
        @Schema(description = "Indica se o cliente está disponível para operações atuais", example = "true")
        boolean active) {
}
