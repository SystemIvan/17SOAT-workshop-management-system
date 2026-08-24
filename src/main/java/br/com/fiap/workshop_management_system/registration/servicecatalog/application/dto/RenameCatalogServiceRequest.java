package br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameCatalogServiceRequest(
        @NotBlank @Size(max = 255) String name) {
}
