package com.demandhub.platform.shared.security;

import java.util.Set;
import java.util.UUID;

/** Usuário autenticado, com papéis e permissões carregados do banco a cada requisição. */
public record CurrentUser(UUID id, String email, String fullName, Set<String> roles, Set<String> permissions) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public String rolesAsString() {
        return String.join(",", roles);
    }
}
