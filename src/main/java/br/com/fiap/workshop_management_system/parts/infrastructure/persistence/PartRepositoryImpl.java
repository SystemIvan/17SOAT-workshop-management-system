package br.com.fiap.workshop_management_system.parts.infrastructure.persistence;

import br.com.fiap.workshop_management_system.parts.domain.model.Part;
import br.com.fiap.workshop_management_system.parts.domain.repository.PartRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure adapter for the {@link PartRepository} port, backed by JPA.
 */
@Repository
public class PartRepositoryImpl implements PartRepository {

    private final PartJpaRepository jpaRepository;
    private final PartPersistenceMapper mapper;

    public PartRepositoryImpl(PartJpaRepository jpaRepository, PartPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Part> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Part> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(Part part) {
        jpaRepository.save(mapper.toEntity(part));
    }
}
