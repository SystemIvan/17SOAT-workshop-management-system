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

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTechnicianUseCaseTest {

    @Mock
    private TechnicianRepository repository;

    private GetTechnicianUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTechnicianUseCase(repository);
    }

    @Test
    void returnsTheTechnicianWhenFound() {
        Technician technician = Technician.reconstitute(
                UUID.randomUUID(), "Joao Mecanico", Set.of(Specialty.MECHANICAL), TechnicianStatus.AVAILABLE);
        when(repository.findById(technician.id())).thenReturn(Optional.of(technician));

        TechnicianResponse response = useCase.execute(technician.id());

        assertThat(response.id()).isEqualTo(technician.id());
        assertThat(response.name()).isEqualTo("Joao Mecanico");
        assertThat(response.status()).isEqualTo(TechnicianStatus.AVAILABLE);
    }

    @Test
    void throwsWhenTechnicianDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Technician not found: " + id);
    }
}
