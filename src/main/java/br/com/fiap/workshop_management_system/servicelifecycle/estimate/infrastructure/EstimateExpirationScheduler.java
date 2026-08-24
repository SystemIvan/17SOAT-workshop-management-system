package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.ExpireEstimatesUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EstimateExpirationScheduler {

    private final ExpireEstimatesUseCase expireEstimatesUseCase;

    public EstimateExpirationScheduler(
            ExpireEstimatesUseCase expireEstimatesUseCase) {
        this.expireEstimatesUseCase = expireEstimatesUseCase;
    }

    @Scheduled(
            fixedDelayString = "${workshop.estimate.expiration-check-delay-ms:60000}"
    )
    public void expireDueEstimates() {
        expireEstimatesUseCase.execute();
    }
}