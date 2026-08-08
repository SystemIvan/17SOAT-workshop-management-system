package br.com.fiap.workshop_management_system.infrastructure.customer.web;

import br.com.fiap.workshop_management_system.application.customer.dto.CreateCustomerRequest;
import br.com.fiap.workshop_management_system.application.customer.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.application.customer.dto.RenameCustomerRequest;
import br.com.fiap.workshop_management_system.application.customer.dto.UpdateCustomerContactRequest;
import br.com.fiap.workshop_management_system.application.customer.usecase.CreateCustomerUseCase;
import br.com.fiap.workshop_management_system.application.customer.usecase.GetCustomerUseCase;
import br.com.fiap.workshop_management_system.application.customer.usecase.ListCustomersUseCase;
import br.com.fiap.workshop_management_system.application.customer.usecase.RenameCustomerUseCase;
import br.com.fiap.workshop_management_system.application.customer.usecase.UpdateCustomerContactUseCase;
import jakarta.validation.Valid;
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
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = createCustomerUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getCustomerUseCase.execute(id));
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> list() {
        return ResponseEntity.ok(listCustomersUseCase.execute());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CustomerResponse> rename(@PathVariable UUID id, @Valid @RequestBody RenameCustomerRequest request) {
        return ResponseEntity.ok(renameCustomerUseCase.execute(id, request));
    }

    @PatchMapping("/{id}/contact-info")
    public ResponseEntity<CustomerResponse> updateContactInfo(
            @PathVariable UUID id, @Valid @RequestBody UpdateCustomerContactRequest request) {
        return ResponseEntity.ok(updateCustomerContactUseCase.execute(id, request));
    }
}
