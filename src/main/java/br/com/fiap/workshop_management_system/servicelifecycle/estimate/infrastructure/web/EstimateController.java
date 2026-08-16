package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.EstimateResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.GenerateEstimateRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.GenerateEstimateUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.GetEstimateUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Estimates", description = "Service estimate generation and consultation")
public class EstimateController {

    private final GenerateEstimateUseCase generateEstimateUseCase;
    private final GetEstimateUseCase getEstimateUseCase;

    public EstimateController(
            GenerateEstimateUseCase generateEstimateUseCase,
            GetEstimateUseCase getEstimateUseCase) {
        this.generateEstimateUseCase = generateEstimateUseCase;
        this.getEstimateUseCase = getEstimateUseCase;
    }

    @PostMapping("/service-orders/{serviceOrderId}/estimates")
    @Operation(summary = "Generate an estimate from the open diagnosis")
    public ResponseEntity<EstimateResponse> generate(
            @PathVariable UUID serviceOrderId,
            @Valid @RequestBody GenerateEstimateRequest request) {

        GenerateEstimateUseCase.Result result =
                generateEstimateUseCase.execute(
                        serviceOrderId,
                        request.diagnosisId()
                );

        EstimateResponse response =
                EstimateResponse.from(result.estimate());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(
                        HttpHeaders.LOCATION,
                        URI.create("/api/estimates/" + response.id()).toString()
                )
                .body(response);
    }

    @GetMapping("/estimates/{estimateId}")
    @Operation(summary = "Get an estimate by ID")
    public ResponseEntity<EstimateResponse> get(
            @PathVariable UUID estimateId) {

        return ResponseEntity.ok(
                EstimateResponse.from(
                        getEstimateUseCase.execute(estimateId)
                )
        );
    }
}