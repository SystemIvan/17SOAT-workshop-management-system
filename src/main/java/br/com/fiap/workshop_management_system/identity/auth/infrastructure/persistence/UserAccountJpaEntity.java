package br.com.fiap.workshop_management_system.identity.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;

/**
 * Projeção JPA do agregado
 * {@link br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount}.
 * Permanece separada da classe de domínio para que o domínio não dependa de frameworks.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccountJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "linked_domain_id")
    private UUID linkedDomainId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccountJpaEntity() {
    }

    public UserAccountJpaEntity(
            UUID id, String username, String passwordHash, Role role, UUID linkedDomainId, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.linkedDomainId = linkedDomainId;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UUID getLinkedDomainId() {
        return linkedDomainId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
