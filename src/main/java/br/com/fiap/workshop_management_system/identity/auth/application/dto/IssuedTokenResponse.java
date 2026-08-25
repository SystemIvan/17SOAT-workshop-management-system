package br.com.fiap.workshop_management_system.identity.auth.application.dto;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;

import java.time.Instant;

public record IssuedTokenResponse(String token, Role role, Instant expiresAt) {
}
