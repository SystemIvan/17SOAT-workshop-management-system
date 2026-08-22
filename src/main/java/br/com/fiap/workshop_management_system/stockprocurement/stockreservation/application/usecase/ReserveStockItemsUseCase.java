package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemReservationEligibility;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReservationAttemptOutcome;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssue;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssueReason;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationConflictException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationCreatedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationNotReservedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservationLine;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReserveStockItemsUseCase implements StockReservationApi {

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ReserveStockItemsUseCase(
            StockItemRepository stockItemRepository,
            StockReservationRepository stockReservationRepository,
            ApplicationEventPublisher eventPublisher) {
        this(stockItemRepository, stockReservationRepository, Clock.systemUTC(), eventPublisher);
    }

    ReserveStockItemsUseCase(
            StockItemRepository stockItemRepository,
            StockReservationRepository stockReservationRepository,
            Clock clock) {
        this(stockItemRepository, stockReservationRepository, clock, event -> {
        });
    }

    ReserveStockItemsUseCase(
            StockItemRepository stockItemRepository,
            StockReservationRepository stockReservationRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.stockItemRepository = stockItemRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public List<ReserveStockItemsResult> reserveAll(List<ReserveStockItemsCommand> commands) {
        List<NormalizedCommand> normalizedCommands = normalize(commands);
        Map<UUID, StockItem> lockedStockItems = lockStockItems(normalizedCommands);
        Map<UUID, StockReservation> existingReservations = findExistingReservations(normalizedCommands);
        List<ReserveStockItemsResult> results = new ArrayList<>();
        Set<UUID> changedStockItemIds = new HashSet<>();

        for (NormalizedCommand command : normalizedCommands) {
            StockReservation existingReservation = existingReservations.get(command.serviceExecutionId());
            if (existingReservation != null) {
                results.add(resultForExistingReservation(command, existingReservation));
                continue;
            }

            List<StockReservationIssue> issues = assess(command, lockedStockItems);
            if (!issues.isEmpty()) {
                results.add(notReserved(command, issues));
                eventPublisher.publishEvent(new StockReservationNotReservedEvent(command.serviceExecutionId(), issues));
                continue;
            }

            List<StockReservationLine> lines = command.items().stream()
                    .map(item -> new StockReservationLine(item.stockItemId(), item.quantity()))
                    .toList();
            for (ReserveStockItem item : command.items()) {
                lockedStockItems.get(item.stockItemId()).reserve(new Quantity(item.quantity()));
                changedStockItemIds.add(item.stockItemId());
            }
            StockReservation reservation = StockReservation.create(command.serviceExecutionId(), lines, currentTime());
            stockReservationRepository.save(reservation);
            existingReservations.put(command.serviceExecutionId(), reservation);
            results.add(new ReserveStockItemsResult(
                    command.serviceExecutionId(),
                    ReservationAttemptOutcome.RESERVED,
                    reservation.id(),
                    true,
                    command.items(),
                    List.of()));
            eventPublisher.publishEvent(new StockReservationCreatedEvent(
                    reservation.id(), command.serviceExecutionId(), command.items()));
        }

        for (UUID stockItemId : changedStockItemIds) {
            stockItemRepository.save(lockedStockItems.get(stockItemId));
        }
        return List.copyOf(results);
    }

    private List<NormalizedCommand> normalize(List<ReserveStockItemsCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("At least one reservation command is required");
        }
        Set<UUID> serviceExecutionIds = new HashSet<>();
        List<NormalizedCommand> normalizedCommands = new ArrayList<>();
        for (ReserveStockItemsCommand command : commands) {
            if (command == null || !serviceExecutionIds.add(command.serviceExecutionId())) {
                throw new IllegalArgumentException(
                        "Each service execution can appear only once in a reservation batch");
            }
            Map<UUID, Integer> quantitiesByStockItem = new LinkedHashMap<>();
            for (ReserveStockItem item : command.items()) {
                int currentQuantity = quantitiesByStockItem.getOrDefault(item.stockItemId(), 0);
                try {
                    quantitiesByStockItem.put(item.stockItemId(), Math.addExact(currentQuantity, item.quantity()));
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("Reserved quantity exceeds the supported range", exception);
                }
            }
            List<ReserveStockItem> consolidatedItems = quantitiesByStockItem.entrySet().stream()
                    .map(entry -> new ReserveStockItem(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparing(ReserveStockItem::stockItemId))
                    .toList();
            normalizedCommands.add(new NormalizedCommand(command.serviceExecutionId(), consolidatedItems));
        }
        return normalizedCommands;
    }

    private Map<UUID, StockItem> lockStockItems(Collection<NormalizedCommand> commands) {
        List<UUID> stockItemIds = commands.stream()
                .flatMap(command -> command.items().stream())
                .map(ReserveStockItem::stockItemId)
                .distinct()
                .sorted()
                .toList();
        Map<UUID, StockItem> lockedStockItems = new HashMap<>();
        stockItemRepository.findAllByIdForUpdate(stockItemIds)
                .forEach(stockItem -> lockedStockItems.put(stockItem.id(), stockItem));
        return lockedStockItems;
    }

    private Map<UUID, StockReservation> findExistingReservations(Collection<NormalizedCommand> commands) {
        Map<UUID, StockReservation> reservations = new HashMap<>();
        stockReservationRepository.findByServiceExecutionIdIn(
                        commands.stream().map(NormalizedCommand::serviceExecutionId).toList())
                .forEach(reservation -> reservations.put(reservation.serviceExecutionId(), reservation));
        return reservations;
    }

    private List<StockReservationIssue> assess(NormalizedCommand command, Map<UUID, StockItem> stockItems) {
        List<StockReservationIssue> issues = new ArrayList<>();
        for (ReserveStockItem item : command.items()) {
            StockItem stockItem = stockItems.get(item.stockItemId());
            if (stockItem == null) {
                issues.add(new StockReservationIssue(
                        item.stockItemId(),
                        StockReservationIssueReason.STOCK_ITEM_NOT_FOUND,
                        item.quantity(),
                        null));
                continue;
            }
            var assessment = stockItem.assessReservation(new Quantity(item.quantity()));
            if (!assessment.eligible()) {
                issues.add(new StockReservationIssue(
                        item.stockItemId(),
                        toIssueReason(assessment.eligibility()),
                        item.quantity(),
                        assessment.availableQuantity().value()));
            }
        }
        return List.copyOf(issues);
    }

    private StockReservationIssueReason toIssueReason(StockItemReservationEligibility eligibility) {
        return switch (eligibility) {
            case INACTIVE -> StockReservationIssueReason.STOCK_ITEM_INACTIVE;
            case INSUFFICIENT_QUANTITY -> StockReservationIssueReason.INSUFFICIENT_QUANTITY;
            case ELIGIBLE -> throw new IllegalArgumentException("Eligible stock item has no reservation issue");
        };
    }

    private ReserveStockItemsResult resultForExistingReservation(
            NormalizedCommand command,
            StockReservation reservation) {
        List<ReserveStockItem> reservedItems = reservation.lines().stream()
                .map(line -> new ReserveStockItem(line.stockItemId(), line.quantity()))
                .sorted(Comparator.comparing(ReserveStockItem::stockItemId))
                .toList();
        if (!reservedItems.equals(command.items())) {
            throw new StockReservationConflictException(
                    "A reservation with different lines already exists for service execution "
                            + command.serviceExecutionId());
        }
        return new ReserveStockItemsResult(
                command.serviceExecutionId(),
                ReservationAttemptOutcome.RESERVED,
                reservation.id(),
                false,
                reservedItems,
                List.of());
    }

    private ReserveStockItemsResult notReserved(
            NormalizedCommand command,
            List<StockReservationIssue> issues) {
        return new ReserveStockItemsResult(
                command.serviceExecutionId(),
                ReservationAttemptOutcome.NOT_RESERVED,
                null,
                false,
                command.items(),
                issues);
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record NormalizedCommand(UUID serviceExecutionId, List<ReserveStockItem> items) {
    }
}
