package br.com.fiap.workshop_management_system.identity.auth.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.identity.auth.application.port.PasswordHasher;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.identity.auth.domain.repository.UserAccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Demonstration accounts for manually exercising the protected endpoints (video/demo purposes).
 * The identity module does not validate linkedDomainId against registration/servicelifecycle
 * (AD-011 is still pending — see technical-spec.md), so CUSTOMER/TECHNICIAN accounts here link to
 * a placeholder UUID rather than an existing Customer/Technician.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
class IdentityDevelopmentDataSeeder implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "changeme123";

    private final UserAccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    IdentityDevelopmentDataSeeder(UserAccountRepository repository, PasswordHasher passwordHasher) {
        this(repository, passwordHasher, Clock.systemUTC());
    }

    IdentityDevelopmentDataSeeder(UserAccountRepository repository, PasswordHasher passwordHasher, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfMissing("manager.dev", Role.MANAGER, null);
        seedIfMissing("technician.dev", Role.TECHNICIAN, UUID.randomUUID());
        seedIfMissing("customer.dev", Role.CUSTOMER, UUID.randomUUID());
    }

    private void seedIfMissing(String rawUsername, Role role, UUID linkedDomainId) {
        Username username = new Username(rawUsername);
        if (repository.existsByUsername(username)) {
            return;
        }
        String passwordHash = passwordHasher.hash(DEMO_PASSWORD);
        repository.save(UserAccount.create(username, passwordHash, role, linkedDomainId, clock.instant()));
    }
}
