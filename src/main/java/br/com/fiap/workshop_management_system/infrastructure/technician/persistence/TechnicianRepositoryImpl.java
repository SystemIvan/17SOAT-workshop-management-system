package br.com.fiap.workshop_management_system.infrastructure.technician.persistence;

import br.com.fiap.workshop_management_system.domain.technician.model.Technician;
import br.com.fiap.workshop_management_system.domain.technician.repository.TechnicianRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure adapter for the {@link TechnicianRepository} port, backed by JPA.
 */
@Repository
public class TechnicianRepositoryImpl implements TechnicianRepository {

    private final TechnicianJpaRepository jpaRepository;
    private final TechnicianPersistenceMapper mapper;

    public TechnicianRepositoryImpl(TechnicianJpaRepository jpaRepository, TechnicianPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Technician> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Technician> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(Technician technician) {
        jpaRepository.save(mapper.toEntity(technician));
    }
}
