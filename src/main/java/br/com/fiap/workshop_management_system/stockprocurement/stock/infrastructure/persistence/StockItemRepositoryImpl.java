package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception
        .StockItemSkuAlreadyExistsException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StockItemRepositoryImpl implements StockItemRepository {
    private static final char LIKE_ESCAPE = '\\';
    private final StockItemJpaRepository jpaRepository;
    private final StockItemPersistenceMapper mapper;

    public StockItemRepositoryImpl(StockItemJpaRepository jpaRepository, StockItemPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<StockItem> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }
    @Override
    public boolean existsBySku(Sku sku) {
        return jpaRepository.existsBySku(sku.value());
    }

    @Override
    public List<StockItem> search(StockItemSearchCriteria criteria) {
        Specification<StockItemJpaEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("active"), criteria.active()));
            if (criteria.search() != null) {
                String pattern = "%" + escapeLike(criteria.search().toLowerCase()) + "%";
                Predicate name = builder.like(builder.lower(root.get("name")), pattern, LIKE_ESCAPE);
                Predicate sku = builder.like(builder.lower(root.get("sku")), pattern, LIKE_ESCAPE);
                predicates.add(builder.or(name, sku));
            }
            if (!criteria.types().isEmpty()) {
                predicates.add(root.get("type").in(criteria.types()));
            }
            if (criteria.available() != null) {
                predicates.add(criteria.available() ? builder.greaterThan(root.get("availableQuantity"), 0)
                        : builder.equal(root.get("availableQuantity"), 0));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Sort sort = Sort.by("name").ascending().and(Sort.by("sku").ascending());
        return jpaRepository.findAll(specification, sort).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public void save(StockItem stockItem) {
        try {
            jpaRepository.saveAndFlush(mapper.toEntity(stockItem));
        } catch (DataIntegrityViolationException exception) {
            throw new StockItemSkuAlreadyExistsException();
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
