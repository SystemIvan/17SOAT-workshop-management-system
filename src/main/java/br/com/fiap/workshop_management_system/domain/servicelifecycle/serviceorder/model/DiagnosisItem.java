package br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model;

import java.util.List;
import java.util.UUID;

/**
 * Input for {@link ServiceOrder#performDiagnosis(List)} - one row of a diagnosis batch.
 */
public record DiagnosisItem(
        UUID catalogServiceId,
        String name,
        Money price,
        List<StockRequirement> stockRequirements
) {
}