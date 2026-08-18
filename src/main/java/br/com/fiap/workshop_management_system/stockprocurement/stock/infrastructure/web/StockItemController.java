package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.web;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.CreateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.UpdateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.CreateStockItemUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.DeactivateStockItemUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.GetStockItemUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.SearchStockItemsUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.UpdateStockItemUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemSearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/stock-items")
@Tag(name = "Stock Items", description = "Stock item catalog management and diagnostic selection")
public class StockItemController {
    private final CreateStockItemUseCase createUseCase;
    private final GetStockItemUseCase getUseCase;
    private final SearchStockItemsUseCase searchUseCase;
    private final UpdateStockItemUseCase updateUseCase;
    private final DeactivateStockItemUseCase deactivateUseCase;

    public StockItemController(CreateStockItemUseCase createUseCase, GetStockItemUseCase getUseCase,
                               SearchStockItemsUseCase searchUseCase, UpdateStockItemUseCase updateUseCase,
                               DeactivateStockItemUseCase deactivateUseCase) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.searchUseCase = searchUseCase;
        this.updateUseCase = updateUseCase;
        this.deactivateUseCase = deactivateUseCase;
    }

    @PostMapping
    @Operation(summary = "Create a stock item")
    public ResponseEntity<StockItemResponse> create(@Valid @RequestBody CreateStockItemRequest request) {
        StockItemResponse response = createUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, URI.create("/api/stock-items/" + response.id()).toString())
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active or inactive stock item by ID")
    public ResponseEntity<StockItemResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getUseCase.execute(id));
    }

    @GetMapping
    @Operation(summary = "List stock items with cumulative filters")
    public ResponseEntity<List<StockItemResponse>> search(
            @Parameter(description = "Name or SKU fragment") @RequestParam(required = false) @Size(max = 100)
            String search,
            @RequestParam(required = false) Set<StockItemType> type,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "true") boolean active) {
        return ResponseEntity.ok(searchUseCase.execute(new StockItemSearchCriteria(search, type, available, active)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update the mutable details of an active stock item")
    public ResponseEntity<StockItemResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateStockItemRequest request) {
        return ResponseEntity.ok(updateUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Logically deactivate a stock item")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        deactivateUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
