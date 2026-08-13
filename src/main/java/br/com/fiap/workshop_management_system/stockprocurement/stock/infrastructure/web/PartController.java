package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.web;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.AdjustPartStockRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.CreatePartRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.PartResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.RenamePartRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.UpdatePartPriceRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.CreatePartUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.DecreasePartStockUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.GetPartUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.IncreasePartStockUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.ListPartsUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.RenamePartUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.UpdatePartPriceUseCase;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parts")
@Tag(name = "Stock Items", description = "Current part and stock operations")
public class PartController {

    private final CreatePartUseCase createPartUseCase;
    private final GetPartUseCase getPartUseCase;
    private final ListPartsUseCase listPartsUseCase;
    private final RenamePartUseCase renamePartUseCase;
    private final IncreasePartStockUseCase increasePartStockUseCase;
    private final DecreasePartStockUseCase decreasePartStockUseCase;
    private final UpdatePartPriceUseCase updatePartPriceUseCase;

    public PartController(
            CreatePartUseCase createPartUseCase,
            GetPartUseCase getPartUseCase,
            ListPartsUseCase listPartsUseCase,
            RenamePartUseCase renamePartUseCase,
            IncreasePartStockUseCase increasePartStockUseCase,
            DecreasePartStockUseCase decreasePartStockUseCase,
            UpdatePartPriceUseCase updatePartPriceUseCase) {
        this.createPartUseCase = createPartUseCase;
        this.getPartUseCase = getPartUseCase;
        this.listPartsUseCase = listPartsUseCase;
        this.renamePartUseCase = renamePartUseCase;
        this.increasePartStockUseCase = increasePartStockUseCase;
        this.decreasePartStockUseCase = decreasePartStockUseCase;
        this.updatePartPriceUseCase = updatePartPriceUseCase;
    }

    @PostMapping
    @Operation(summary = "Create a stock item")
    public ResponseEntity<PartResponse> create(@Valid @RequestBody CreatePartRequest request) {
        PartResponse response = createPartUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a stock item by ID")
    public ResponseEntity<PartResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getPartUseCase.execute(id));
    }

    @GetMapping
    @Operation(summary = "List stock items")
    public ResponseEntity<List<PartResponse>> list() {
        return ResponseEntity.ok(listPartsUseCase.execute());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a stock item")
    public ResponseEntity<PartResponse> rename(@PathVariable UUID id, @Valid @RequestBody RenamePartRequest request) {
        return ResponseEntity.ok(renamePartUseCase.execute(id, request));
    }

    @PostMapping("/{id}/stock/increase")
    @Operation(summary = "Increase stock quantity")
    public ResponseEntity<PartResponse> increaseStock(@PathVariable UUID id, @Valid @RequestBody AdjustPartStockRequest request) {
        return ResponseEntity.ok(increasePartStockUseCase.execute(id, request));
    }

    @PostMapping("/{id}/stock/decrease")
    @Operation(summary = "Decrease stock quantity")
    public ResponseEntity<PartResponse> decreaseStock(@PathVariable UUID id, @Valid @RequestBody AdjustPartStockRequest request) {
        return ResponseEntity.ok(decreasePartStockUseCase.execute(id, request));
    }

    @PatchMapping("/{id}/price")
    @Operation(summary = "Update stock item price")
    public ResponseEntity<PartResponse> updatePrice(@PathVariable UUID id, @Valid @RequestBody UpdatePartPriceRequest request) {
        return ResponseEntity.ok(updatePartPriceUseCase.execute(id, request));
    }
}
