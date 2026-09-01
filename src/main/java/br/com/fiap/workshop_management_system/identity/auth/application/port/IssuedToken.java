package br.com.fiap.workshop_management_system.identity.auth.application.port;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;

import java.time.Instant;

public record IssuedToken(String token, Role role, Instant expiresAt) {
}
