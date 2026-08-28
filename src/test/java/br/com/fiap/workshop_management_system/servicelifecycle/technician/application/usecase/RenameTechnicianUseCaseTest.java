package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.RenameTechnicianRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Specialty;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.TechnicianStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameTechnicianUseCaseTest {

    @Mock
    private TechnicianRepository repository;

    private RenameTechnicianUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RenameTechnicianUseCase(repository);
    }

    @Test
    void renamesAnExistingTechnician() {
        Technician technician = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.AVAILABLE);
        when(repository.findById(technician.id())).thenReturn(Optional.of(technician));

        TechnicianResponse response = useCase.execute(technician.id(), new RenameTechnicianRequest("Joao Silva"));

        assertThat(response.name()).isEqualTo("Joao Silva");
        verify(repository).save(technician);
    }

    @Test
    void throwsWhenTechnicianDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, new RenameTechnicianRequest("Joao Silva")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Technician not found: " + id);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void propagatesDomainValidationFailureWithoutSaving() {
        Technician technician = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.AVAILABLE);
        when(repository.findById(technician.id())).thenReturn(Optional.of(technician));

        assertThatThrownBy(() -> useCase.execute(technician.id(), new RenameTechnicianRequest(" ")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
