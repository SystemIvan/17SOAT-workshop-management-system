package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.web;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseDemandResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase.SearchOpenPurchaseDemandsUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchase-demands")
@Tag(name = "Purchase Demands", description = "Open procurement needs detected by trusted internal flows")
public class PurchaseDemandController {

    private final SearchOpenPurchaseDemandsUseCase searchUseCase;

    public PurchaseDemandController(SearchOpenPurchaseDemandsUseCase searchUseCase) {
        this.searchUseCase = searchUseCase;
    }

    @GetMapping
    @Operation(
            summary = "List open purchase demands",
            description = "Filters are cumulative and only OPEN demands are returned")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Open demands returned"),
            @ApiResponse(responseCode = "400", description = "Invalid filter")
    })
    public ResponseEntity<List<PurchaseDemandResponse>> search(
            @Parameter(description = "Demand origin") @RequestParam(required = false) PurchaseDemandOrigin origin,
            @Parameter(description = "Stock item ID") @RequestParam(required = false) UUID stockItemId) {
        return ResponseEntity.ok(searchUseCase.execute(origin, stockItemId));
    }
}
