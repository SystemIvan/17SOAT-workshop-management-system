package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

import java.time.Instant;

public record ExecutionTimePeriod(Instant fromInclusive, Instant toExclusive) {

    public ExecutionTimePeriod {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new InvalidExecutionTimePeriodException();
        }
    }
}
