package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.ExpireEstimatesUseCase;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EstimateExpirationSchedulerTest {

    @Test
    void delegatesExpirationToUseCase() {
        ExpireEstimatesUseCase useCase = mock(ExpireEstimatesUseCase.class);

        EstimateExpirationScheduler scheduler =
                new EstimateExpirationScheduler(useCase);

        scheduler.expireDueEstimates();

        verify(useCase).execute();
    }
}