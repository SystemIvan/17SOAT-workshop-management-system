package br.com.fiap.workshop_management_system.identity.auth.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.dto.CreateUserAccountRequest;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.IssuedTokenResponse;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.LoginRequest;
import br.com.fiap.workshop_management_system.identity.auth.application.dto.UserAccountResponse;
import br.com.fiap.workshop_management_system.identity.auth.application.usecase.AuthenticateUserAccountUseCase;
import br.com.fiap.workshop_management_system.identity.auth.application.usecase.CreateUserAccountUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication and user account provisioning (AD-016)")
public class AuthController {

    private final AuthenticateUserAccountUseCase authenticateUserAccountUseCase;
    private final CreateUserAccountUseCase createUserAccountUseCase;

    public AuthController(
            AuthenticateUserAccountUseCase authenticateUserAccountUseCase,
            CreateUserAccountUseCase createUserAccountUseCase) {
        this.authenticateUserAccountUseCase = authenticateUserAccountUseCase;
        this.createUserAccountUseCase = createUserAccountUseCase;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated; token issued"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing credentials fields"),
            @ApiResponse(responseCode = "401", description = "Unknown username or wrong password")
    })
    public ResponseEntity<IssuedTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticateUserAccountUseCase.execute(request));
    }

    @PostMapping("/users")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a user account", description = "Requires an ADMIN token")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User account created"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing account fields"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Token role is not ADMIN"),
            @ApiResponse(responseCode = "409", description = "Username already registered")
    })
    public ResponseEntity<UserAccountResponse> createUser(@Valid @RequestBody CreateUserAccountRequest request) {
        UserAccountResponse response = createUserAccountUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
