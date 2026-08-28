package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceOrderRepositoryTest {

    private final ServiceOrder serviceOrder = ServiceOrder.create(
            UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
            "Initial assessment");

    private final ServiceOrderRepository repository = new ServiceOrderRepository() {
        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return id.equals(serviceOrder.id()) ? Optional.of(serviceOrder) : Optional.empty();
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
        }
    };

    @Test
    void findByIdForUpdateDelegatesToFindByIdByDefault() {
        assertThat(repository.findByIdForUpdate(serviceOrder.id())).contains(serviceOrder);
        assertThat(repository.findByIdForUpdate(UUID.randomUUID())).isEmpty();
    }

    @Test
    void searchIsUnsupportedByDefault() {
        assertThatThrownBy(() -> repository.search(new ServiceOrderSearchCriteria(null, null, null, null)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("search not supported by this ServiceOrderRepository");
    }

    @Test
    void findAwaitingItemsByStockItemIdsIsUnsupportedByDefault() {
        assertThatThrownBy(() -> repository.findAwaitingItemsByStockItemIds(List.of(UUID.randomUUID())))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("awaiting-items search not supported by this ServiceOrderRepository");
    }
}
