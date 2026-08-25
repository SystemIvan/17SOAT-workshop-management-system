package br.com.fiap.workshop_management_system.identity.auth.application.dto;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateUserAccountRequest(
        @NotBlank(message = "Username must not be blank") String username,
        @NotBlank(message = "Password must not be blank") String password,
        @NotNull(message = "Role must not be null") Role role,
        UUID linkedDomainId) {
}
