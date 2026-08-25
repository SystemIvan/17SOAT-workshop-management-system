package br.com.fiap.workshop_management_system.identity.auth.application.port;

/**
 * Isolates password hashing from the domain model (AGENTS.md — keep the domain free from framework
 * concerns). Implemented in infrastructure with a real hashing algorithm (bcrypt).
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
