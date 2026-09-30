package com.demandhub.platform.catalog.repository;

import com.demandhub.platform.catalog.domain.DemandType;
import com.demandhub.platform.catalog.domain.Priority;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public final class CatalogRepositories {

    private CatalogRepositories() {}

    public interface DemandTypeRepository extends JpaRepository<DemandType, Long> {
        Optional<DemandType> findByCode(String code);

        List<DemandType> findByActiveTrueOrderByNameAsc();

        List<DemandType> findAllByOrderByNameAsc();
    }

    public interface PriorityRepository extends JpaRepository<Priority, String> {
        List<Priority> findAllByOrderByRankOrderAsc();
    }
}
