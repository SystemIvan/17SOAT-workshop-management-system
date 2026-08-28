package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.UpdateTechnicianStatusRequest;
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
class UpdateTechnicianStatusUseCaseTest {

    @Mock
    private TechnicianRepository repository;

    private UpdateTechnicianStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateTechnicianStatusUseCase(repository);
    }

    @Test
    void marksAnAvailableTechnicianAsBusy() {
        Technician technician = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.AVAILABLE);
        when(repository.findById(technician.id())).thenReturn(Optional.of(technician));

        TechnicianResponse response =
                useCase.execute(technician.id(), new UpdateTechnicianStatusRequest(TechnicianStatus.BUSY));

        assertThat(response.status()).isEqualTo(TechnicianStatus.BUSY);
        verify(repository).save(technician);
    }

    @Test
    void marksABusyTechnicianAsAvailable() {
        Technician technician = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.BUSY);
        when(repository.findById(technician.id())).thenReturn(Optional.of(technician));

        TechnicianResponse response =
                useCase.execute(technician.id(), new UpdateTechnicianStatusRequest(TechnicianStatus.AVAILABLE));

        assertThat(response.status()).isEqualTo(TechnicianStatus.AVAILABLE);
        verify(repository).save(technician);
    }

    @Test
    void deactivatesATechnician() {
        Technician technician = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.AVAILABLE);
        when(repository.findById(technician.id())).thenReturn(Optional.of(technician));

        TechnicianResponse response =
                useCase.execute(technician.id(), new UpdateTechnicianStatusRequest(TechnicianStatus.INACTIVE));

        assertThat(response.status()).isEqualTo(TechnicianStatus.INACTIVE);
        verify(repository).save(technician);
    }

    @Test
    void throwsWhenTechnicianDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, new UpdateTechnicianStatusRequest(TechnicianStatus.BUSY)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Technician not found: " + id);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMarkingAnInactiveTechnicianAsBusy() {
        Technician technician = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.INACTIVE);
        when(repository.findById(technician.id())).thenReturn(Optional.of(technician));

        assertThatThrownBy(() -> useCase.execute(technician.id(), new UpdateTechnicianStatusRequest(TechnicianStatus.BUSY)))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMarkingAnInactiveTechnicianAsAvailable() {
        Technician technician = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.INACTIVE);
        when(repository.findById(technician.id())).thenReturn(Optional.of(technician));

        assertThatThrownBy(() ->
                useCase.execute(technician.id(), new UpdateTechnicianStatusRequest(TechnicianStatus.AVAILABLE)))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
