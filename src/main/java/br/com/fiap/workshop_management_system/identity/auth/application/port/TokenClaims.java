package br.com.fiap.workshop_management_system.identity.auth.application.port;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

public record TokenClaims(UUID userAccountId, Role role, UUID linkedDomainId, Instant expiresAt) {
}
