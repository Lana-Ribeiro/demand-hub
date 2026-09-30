package com.demandhub.platform.project.repository;

import com.demandhub.platform.project.domain.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderByNameAsc();

    List<Project> findByActiveTrueOrderByNameAsc();

    Optional<Project> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
