package br.com.fiap.workshop_management_system.infrastructure.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class OpenApiContractTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void documentEveryCurrentHttpOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/customers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/customers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/identify'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].get").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}/mileage'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services'].post").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services'].get").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}/base-price'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/technicians'].post").exists())
                .andExpect(jsonPath("$.paths['/api/technicians'].get").exists())
                .andExpect(jsonPath("$.paths['/api/technicians/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/technicians/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/technicians/{id}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items'].post").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items'].get").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-demands'].get").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{purchaseOrderId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/parts']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/service-orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/status'].get").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/diagnosis'].post").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/diagnosis-assignee'].put").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/priority'].patch").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/service-orders/{id}/executions/{executionId}/assign-technician'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/start'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/progress'].patch")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/complete'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/finalize'].post").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{serviceOrderId}/estimates'].post").exists())
                .andExpect(jsonPath("$.paths['/api/estimates/{estimateId}'].get").exists());
    }

    @Test
    void documentPurchaseOrderCreationContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/purchase-demands'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post.responses['422']").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post.parameters[0].name")
                        .value("Idempotency-Key"))
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post.parameters[0].required")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.CreatePurchaseOrderRequest.properties.demandIds")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.CreatePurchaseOrderRequest.properties.lines")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.CreatePurchaseOrderRequest.properties.status")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PurchaseOrderResponse.properties.externalReference")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.PurchaseOrderResponse.properties.status.enum",
                        not(hasItem("PENDING_SUBMISSION"))))
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{purchaseOrderId}'].get.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{purchaseOrderId}'].get.responses['404']")
                        .exists());
    }

    @Test
    void documentServiceOrderInitialAssessmentContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/service-orders'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders'].post.responses['400']").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CreateServiceOrderRequest.properties.initialAssessment").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CreateServiceOrderRequest.required", hasItem("initialAssessment")))
                .andExpect(jsonPath("$.components.schemas.ServiceOrderResponse.properties.initialAssessment").exists())
                .andExpect(jsonPath("$.components.schemas.ServiceOrderResponse.properties.initialAssessment.type",
                        hasItem("null")));
    }

    @Test
    void documentServiceOrderStatusProjectionAliases() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ServiceOrderResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ServiceOrderResponse.properties.status.deprecated")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.ServiceOrderResponse.properties.statusSnapshot").exists());
    }

    @Test
    void documentDiagnosisAuthorshipContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.PerformDiagnosisRequest.properties.diagnosedByTechnicianId")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.PerformDiagnosisRequest.required",
                        hasItem("diagnosedByTechnicianId")))
                .andExpect(jsonPath("$.components.schemas.ServiceExecutionResponse.properties.diagnosedByTechnicianId")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ServiceExecutionResponse.properties.diagnosedAt").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/diagnosis'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/diagnosis'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/diagnosis'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/diagnosis'].post.responses['409']").exists());
    }

    @Test
    void documentPartialCustomerContactUpdateContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateCustomerContactRequest.properties.contactInfo")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.UpdateContactInfoDTO.properties.email").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateContactInfoDTO.properties.phone").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateContactInfoDTO.properties.address").exists())
                .andExpect(jsonPath("$.components.schemas.ContactInfoDTO.properties.address").exists())
                .andExpect(jsonPath("$.components.schemas.AddressDTO.properties.street").exists())
                .andExpect(jsonPath("$.components.schemas.AddressDTO.properties.state").exists())
                .andExpect(jsonPath("$.components.schemas.AddressDTO.properties.postalCode").exists());
    }

    @Test
    void documentCustomerLifecycleContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].delete.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].patch.responses['409']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/customers/{id}/contact-info'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.CustomerResponse.properties.active").exists());
    }

    @Test
    void documentVehicleRegistrationContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.customerId").exists())
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.chassis").exists())
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.chassis.type",
                        hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.mileage").exists())
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.mileage.type",
                        hasItem("integer")))
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.mileage.type",
                        hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.mileage.format")
                        .value("int64"))
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.mileage.minimum")
                        .value(0))
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.required", not(hasItem("mileage"))))
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.id").exists())
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.vehicleId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.customerId").exists())
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.chassis").exists())
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.chassis.type",
                        hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.mileage.type",
                        hasItem("integer")))
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.mileage.type",
                        hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.mileage.format")
                        .value("int64"))
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.active").exists());
    }

    @Test
    void documentVehicleUpdateContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.properties.brand").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.properties.model").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.properties.year").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.properties.color").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.properties.chassis").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.properties.chassis.type",
                        hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.required", hasItem("brand")))
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.required", hasItem("model")))
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.required", hasItem("year")))
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.required", hasItem("color")))
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleRequest.required", not(hasItem("chassis"))));
    }

    @Test
    void documentCatalogServiceContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/catalog-services'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].delete.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}'].delete.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}/base-price'].patch.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}/base-price'].patch.responses['400']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}/base-price'].patch.responses['404']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/catalog-services/{id}/base-price'].patch.responses['409']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.CreateCatalogServiceRequest.properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.CreateCatalogServiceRequest.properties.basePrice").exists())
                .andExpect(jsonPath("$.components.schemas.CreateCatalogServiceRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateCatalogServiceRequest.properties.active")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateCatalogServiceRequest.required", hasItem("name")))
                .andExpect(jsonPath("$.components.schemas.CreateCatalogServiceRequest.required", hasItem("basePrice")))
                .andExpect(jsonPath("$.components.schemas.RenameCatalogServiceRequest.properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.RenameCatalogServiceRequest.properties.basePrice")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RenameCatalogServiceRequest.properties.id")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RenameCatalogServiceRequest.properties.active")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RenameCatalogServiceRequest.required", hasItem("name")))
                .andExpect(jsonPath("$.components.schemas.UpdateCatalogServiceBasePriceRequest.properties.basePrice")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.UpdateCatalogServiceBasePriceRequest.properties.name")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UpdateCatalogServiceBasePriceRequest.properties.id")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UpdateCatalogServiceBasePriceRequest.properties.active")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UpdateCatalogServiceBasePriceRequest.required",
                        hasItem("basePrice")))
                .andExpect(jsonPath("$.components.schemas.MoneyDto.properties.value").exists())
                .andExpect(jsonPath("$.components.schemas.MoneyDto.properties.currency").exists())
                .andExpect(jsonPath("$.components.schemas.MoneyDto.properties.currency.enum", hasItem("BRL")))
                .andExpect(jsonPath("$.components.schemas.MoneyDto.properties.currency.enum", not(hasItem("USD"))))
                .andExpect(jsonPath("$.components.schemas.CatalogServiceResponse.properties.id").exists())
                .andExpect(jsonPath("$.components.schemas.CatalogServiceResponse.properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.CatalogServiceResponse.properties.basePrice").exists())
                .andExpect(jsonPath("$.components.schemas.CatalogServiceResponse.properties.active.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.paths['/api/catalog-services'].get.responses['200'].content"
                        + "['application/json'].schema.type").value("array"))
                .andExpect(jsonPath("$.paths['/api/catalog-services'].get.responses['200'].content"
                        + "['application/json'].schema.items.$ref")
                        .value("#/components/schemas/CatalogServiceResponse"));
    }

    @Test
    void documentVehicleMileageUpdateContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}/mileage'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}/mileage'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}/mileage'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}/mileage'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleMileageRequest.properties.mileage.type")
                        .value("integer"))
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleMileageRequest.properties.mileage.format")
                        .value("int64"))
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleMileageRequest.properties.mileage.minimum")
                        .value(0))
                .andExpect(jsonPath("$.components.schemas.UpdateVehicleMileageRequest.required", hasItem("mileage")));
    }

    @Test
    void documentVehicleLifecycleAndServiceOrderEligibilityContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].get.responses['200']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/vehicles'].get.responses['200'].content['application/json'].schema.type")
                        .value("array"))
                .andExpect(jsonPath(
                        "$.paths['/api/vehicles'].get.responses['200'].content['application/json'].schema.items.$ref")
                        .value("#/components/schemas/VehicleResponse"))
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].delete.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles/{id}'].delete.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders'].post.responses['409']").exists());
    }
}
