package br.com.fiap.workshop_management_system.identity.auth.application.dto;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;

import java.util.UUID;

public record UserAccountResponse(UUID id, String username, Role role, UUID linkedDomainId) {
}
