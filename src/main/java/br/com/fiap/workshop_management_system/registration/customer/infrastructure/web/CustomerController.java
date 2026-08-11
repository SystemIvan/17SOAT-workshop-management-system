package br.com.fiap.workshop_management_system.registration.customer.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.CreateCustomerRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.RenameCustomerRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateCustomerContactRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.CreateCustomerUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.GetCustomerUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.ListCustomersUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.RenameCustomerUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.UpdateCustomerContactUseCase;
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
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Customer registration operations")
public class CustomerController {

    private final CreateCustomerUseCase createCustomerUseCase;
    private final GetCustomerUseCase getCustomerUseCase;
    private final ListCustomersUseCase listCustomersUseCase;
    private final RenameCustomerUseCase renameCustomerUseCase;
    private final UpdateCustomerContactUseCase updateCustomerContactUseCase;

    public CustomerController(
            CreateCustomerUseCase createCustomerUseCase,
            GetCustomerUseCase getCustomerUseCase,
            ListCustomersUseCase listCustomersUseCase,
            RenameCustomerUseCase renameCustomerUseCase,
            UpdateCustomerContactUseCase updateCustomerContactUseCase) {
        this.createCustomerUseCase = createCustomerUseCase;
        this.getCustomerUseCase = getCustomerUseCase;
        this.listCustomersUseCase = listCustomersUseCase;
        this.renameCustomerUseCase = renameCustomerUseCase;
        this.updateCustomerContactUseCase = updateCustomerContactUseCase;
    }

    @PostMapping
    @Operation(summary = "Create a customer")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = createCustomerUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by ID")
    public ResponseEntity<CustomerResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getCustomerUseCase.execute(id));
    }

    @GetMapping
    @Operation(summary = "List customers")
    public ResponseEntity<List<CustomerResponse>> list() {
        return ResponseEntity.ok(listCustomersUseCase.execute());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a customer")
    public ResponseEntity<CustomerResponse> rename(@PathVariable UUID id, @Valid @RequestBody RenameCustomerRequest request) {
        return ResponseEntity.ok(renameCustomerUseCase.execute(id, request));
    }

    @PatchMapping("/{id}/contact-info")
    @Operation(summary = "Update customer contact information")
    public ResponseEntity<CustomerResponse> updateContactInfo(
            @PathVariable UUID id, @Valid @RequestBody UpdateCustomerContactRequest request) {
        return ResponseEntity.ok(updateCustomerContactUseCase.execute(id, request));
    }
}
