package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ExecutionTimePeriod;

import java.util.List;

public interface ServiceExecutionTimeMetricsReadPort {

    ExecutionTimeAggregate findGlobal(ExecutionTimePeriod period);

    List<CatalogServiceExecutionTimeAggregate> findByCatalogService(ExecutionTimePeriod period);
}
