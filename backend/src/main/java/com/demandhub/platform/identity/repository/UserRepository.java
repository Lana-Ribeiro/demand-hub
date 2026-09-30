package com.demandhub.platform.identity.repository;

import com.demandhub.platform.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select distinct u from User u join u.roles r where r.code = :role and u.active = true")
    List<User> findActiveByRole(@Param("role") String role);

    List<User> findAllByOrderByFullNameAsc();
}
