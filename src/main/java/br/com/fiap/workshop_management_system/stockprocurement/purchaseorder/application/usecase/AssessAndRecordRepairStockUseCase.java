package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentApi;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentExecution;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentExecutionResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentResultLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAvailabilityStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemInactiveException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class AssessAndRecordRepairStockUseCase implements RepairStockAssessmentApi {

    private final StockItemRepository stockItemRepository;
    private final PurchaseDemandRepository demandRepository;
    private final Clock clock;

    @Autowired
    public AssessAndRecordRepairStockUseCase(
            StockItemRepository stockItemRepository, PurchaseDemandRepository demandRepository) {
        this(stockItemRepository, demandRepository, Clock.systemUTC());
    }

    AssessAndRecordRepairStockUseCase(
            StockItemRepository stockItemRepository, PurchaseDemandRepository demandRepository, Clock clock) {
        this.stockItemRepository = stockItemRepository;
        this.demandRepository = demandRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RepairStockAssessmentResult assessAndRecord(RepairStockAssessmentCommand command) {
        List<RepairStockAssessmentExecution> executions = command.executions().stream()
                .sorted(Comparator.comparing(RepairStockAssessmentExecution::serviceExecutionId))
                .toList();
        List<UUID> itemIds = executions.stream().flatMap(execution -> execution.lines().stream())
                .map(RepairStockAssessmentLine::stockItemId).distinct().sorted().toList();
        Map<UUID, StockItem> items = lockAndValidateItems(itemIds);
        Instant observedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return new RepairStockAssessmentResult(executions.stream()
                .map(execution -> assessExecution(execution, items, observedAt)).toList());
    }

    private Map<UUID, StockItem> lockAndValidateItems(List<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        List<StockItem> items = stockItemRepository.findAllByIdForUpdate(itemIds);
        if (items.size() != itemIds.size()) {
            throw new StockItemNotFoundException();
        }
        if (items.stream().anyMatch(item -> !item.active())) {
            throw new StockItemInactiveException();
        }
        return items.stream().collect(java.util.stream.Collectors.toMap(StockItem::id, Function.identity()));
    }

    private RepairStockAssessmentExecutionResult assessExecution(
            RepairStockAssessmentExecution execution, Map<UUID, StockItem> items, Instant observedAt) {
        List<RepairStockAssessmentResultLine> lines = execution.lines().stream()
                .sorted(Comparator.comparing(RepairStockAssessmentLine::stockItemId))
                .map(line -> assessLine(execution.serviceExecutionId(), line, items.get(line.stockItemId()), observedAt))
                .toList();
        return new RepairStockAssessmentExecutionResult(execution.serviceExecutionId(), lines);
    }

    private RepairStockAssessmentResultLine assessLine(
            UUID executionId, RepairStockAssessmentLine line, StockItem item, Instant observedAt) {
        int available = item.availableQuantity().value();
        int shortage = Math.max(line.requestedQuantity() - available, 0);
        RepairStockAvailabilityStatus status = shortage == 0
                ? RepairStockAvailabilityStatus.AVAILABLE : RepairStockAvailabilityStatus.INSUFFICIENT_QUANTITY;
        if (shortage > 0) {
            PurchaseDemand demand = demandRepository.findEquivalentForUpdate(
                            PurchaseDemandOrigin.PENDING_REPAIR, executionId, line.stockItemId())
                    .orElseGet(() -> PurchaseDemand.createPendingRepair(
                            executionId, line.stockItemId(), line.requestedQuantity(), available, shortage, observedAt));
            demand.recordObservation(line.requestedQuantity(), available, shortage, observedAt);
            demandRepository.save(demand);
        }
        return new RepairStockAssessmentResultLine(
                line.stockItemId(), line.requestedQuantity(), available, shortage, status, observedAt);
    }
}
