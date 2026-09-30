package com.demandhub.platform.identity.web;

import com.demandhub.platform.identity.domain.Role;
import com.demandhub.platform.identity.repository.RoleRepository;
import com.demandhub.platform.identity.service.UserService;
import com.demandhub.platform.identity.web.UserDtos.CreateUserRequest;
import com.demandhub.platform.identity.web.UserDtos.UpdateUserRequest;
import com.demandhub.platform.identity.web.UserDtos.UserRef;
import com.demandhub.platform.identity.web.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserAdminController {

    private final UserService userService;
    private final RoleRepository roleRepository;

    public UserAdminController(UserService userService, RoleRepository roleRepository) {
        this.userService = userService;
        this.roleRepository = roleRepository;
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasAuthority('ADMIN_USERS')")
    public List<UserResponse> list() {
        return userService.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADMIN_USERS')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return UserResponse.from(userService.create(req.email(), req.fullName(), req.area(), req.password(), req.roles()));
    }

    @PutMapping("/admin/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN_USERS')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return UserResponse.from(userService.update(id, req.fullName(), req.area(), req.roles(), req.active()));
    }

    @GetMapping("/admin/roles")
    @PreAuthorize("hasAnyAuthority('ADMIN_USERS','ADMIN_CONFIG')")
    public List<RoleResponse> roles() {
        return roleRepository.findAll().stream()
                .map(r -> new RoleResponse(r.getCode(), r.getName(), r.getDescription(),
                        new TreeSet<>(r.getPermissions().stream().map(p -> p.getCode()).toList())))
                .toList();
    }

    /** Lista resumida para seleção de responsável (PMO/triagem). */
    @GetMapping("/users/assignable")
    @PreAuthorize("hasAnyAuthority('DEMAND_TRIAGE','ADMIN_CONFIG')")
    public List<UserRef> assignable() {
        return userService.list().stream()
                .filter(u -> u.isActive() && !u.roleCodes().equals(java.util.Set.of(Role.CLIENT)))
                .map(UserRef::from).toList();
    }

    public record RoleResponse(String code, String name, String description, java.util.Set<String> permissions) {}
}
