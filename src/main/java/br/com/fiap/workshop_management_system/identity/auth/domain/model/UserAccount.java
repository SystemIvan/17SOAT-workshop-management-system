package br.com.fiap.workshop_management_system.identity.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root owning credentials and the role-to-domain-ID mapping (AD-016). Customer and Technician
 * remain pure domain references here: this aggregate never imports their types, only their IDs.
 */
public class UserAccount {

    private final UUID id;
    private final Username username;
    private final Role role;
    private final UUID linkedDomainId;
    private final Instant createdAt;

    private String passwordHash;

    public static UserAccount create(
            Username username, String passwordHash, Role role, UUID linkedDomainId, Instant createdAt) {
        requireUsername(username);
        requirePasswordHash(passwordHash);
        requireLinkedDomainIdConsistency(role, linkedDomainId);
        return new UserAccount(UUID.randomUUID(), username, passwordHash, role, linkedDomainId, createdAt);
    }

    /**
     * Reconstructs a UserAccount from persisted state. Exclusive use of the persistence adapter; unlike
     * {@link #create}, it does not re-run creation rules.
     */
    public static UserAccount reconstitute(
            UUID id, Username username, String passwordHash, Role role, UUID linkedDomainId, Instant createdAt) {
        return new UserAccount(id, username, passwordHash, role, linkedDomainId, createdAt);
    }

    private UserAccount(
            UUID id, Username username, String passwordHash, Role role, UUID linkedDomainId, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.linkedDomainId = linkedDomainId;
        this.createdAt = createdAt;
    }

    private static void requireUsername(Username username) {
        if (username == null) {
            throw new InvalidUserAccountException("Username must not be null");
        }
    }

    private static void requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new InvalidUserAccountException("Password hash must not be blank");
        }
    }

    private static void requireLinkedDomainIdConsistency(Role role, UUID linkedDomainId) {
        if (role == null) {
            throw new InvalidUserAccountException("Role must not be null");
        }
        boolean requiresLinkedDomainId = role == Role.CUSTOMER || role == Role.TECHNICIAN;
        if (requiresLinkedDomainId && linkedDomainId == null) {
            throw new InvalidUserAccountException("linkedDomainId is required for role " + role);
        }
        if (!requiresLinkedDomainId && linkedDomainId != null) {
            throw new InvalidUserAccountException("linkedDomainId must be null for role " + role);
        }
    }

    public UUID id() {
        return id;
    }

    public Username username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public UUID linkedDomainId() {
        return linkedDomainId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
