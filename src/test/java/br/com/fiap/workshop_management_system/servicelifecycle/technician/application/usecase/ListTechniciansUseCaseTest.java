package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase;

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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTechniciansUseCaseTest {

    @Mock
    private TechnicianRepository repository;

    private ListTechniciansUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListTechniciansUseCase(repository);
    }

    @Test
    void returnsAllTechniciansMappedToResponses() {
        Technician first = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.AVAILABLE);
        Technician second = Technician.reconstitute(
                UUID.randomUUID(), "Maria Eletricista", Set.of(Specialty.ELECTRICAL), TechnicianStatus.BUSY);
        when(repository.findAll()).thenReturn(List.of(first, second));

        List<TechnicianResponse> responses = useCase.execute();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(first.id());
        assertThat(responses.get(1).id()).isEqualTo(second.id());
    }

    @Test
    void returnsEmptyListWhenNoTechnicianExists() {
        when(repository.findAll()).thenReturn(List.of());

        List<TechnicianResponse> responses = useCase.execute();

        assertThat(responses).isEmpty();
    }
}
