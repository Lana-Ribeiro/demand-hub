package com.demandhub.platform.identity.repository;

import com.demandhub.platform.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {
}
