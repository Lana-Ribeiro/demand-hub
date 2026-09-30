package com.demandhub.platform.identity.web;

import com.demandhub.platform.identity.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 200) String password) {}

    public record LoginResponse(String token, Instant expiresAt, UserResponse user) {}

    public record UserResponse(UUID id, String email, String fullName, String area, boolean active,
                               Set<String> roles, Set<String> permissions) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getArea(), u.isActive(),
                    new TreeSet<>(u.roleCodes()), new TreeSet<>(u.permissionCodes()));
        }
    }

    /** Referência resumida de usuário para listas e seleção. */
    public record UserRef(UUID id, String fullName, String email) {
        public static UserRef from(User u) {
            return u == null ? null : new UserRef(u.getId(), u.getFullName(), u.getEmail());
        }
    }

    public record CreateUserRequest(@NotBlank @Email String email, @NotBlank @Size(max = 200) String fullName,
                                    @Size(max = 200) String area,
                                    @NotBlank @Size(min = 10, max = 200) String password,
                                    @NotEmpty Set<String> roles) {}

    public record UpdateUserRequest(@Size(max = 200) String fullName, @Size(max = 200) String area,
                                    Set<String> roles, Boolean active) {}
}
