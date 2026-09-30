package com.demandhub.platform.identity.service;

import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.identity.domain.Role;
import com.demandhub.platform.identity.domain.User;
import com.demandhub.platform.identity.repository.RoleRepository;
import com.demandhub.platform.identity.repository.UserRepository;
import com.demandhub.platform.shared.error.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email.trim())
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                // Mensagem genérica: não revela se o e-mail existe.
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha inválidos."));
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResult(token, user);
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Usuário", id));
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAllByOrderByFullNameAsc();
    }

    @Transactional
    public User create(String email, String fullName, String area, String password, Set<String> roleCodes) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "Já existe usuário com este e-mail.");
        }
        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setFullName(fullName.trim());
        user.setArea(area);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoles(resolveRoles(roleCodes));
        User saved = users.save(user);
        audit.event("USER_CREATED").entity("User", saved.getId()).change("roles", null, new TreeSet<>(roleCodes)).record();
        return saved;
    }

    @Transactional
    public User update(UUID id, String fullName, String area, Set<String> roleCodes, Boolean active) {
        User user = get(id);
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        user.setArea(area);
        if (roleCodes != null) {
            Set<String> before = new TreeSet<>(user.roleCodes());
            Set<String> after = new TreeSet<>(roleCodes);
            if (!before.equals(after)) {
                user.setRoles(resolveRoles(roleCodes));
                audit.event("USER_ROLES_CHANGED").entity("User", id).change("roles", before, after).record();
            }
        }
        if (active != null && active != user.isActive()) {
            user.setActive(active);
            audit.event(active ? "USER_ACTIVATED" : "USER_DEACTIVATED").entity("User", id).record();
        }
        return user;
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw ApiException.businessRule("ROLE_REQUIRED", "Informe ao menos um papel.");
        }
        Set<Role> result = new HashSet<>();
        for (String code : codes) {
            result.add(roles.findById(code).orElseThrow(() -> ApiException.badRequest("INVALID_ROLE", "Papel inválido: " + code)));
        }
        return result;
    }

    public record LoginResult(JwtService.IssuedToken token, User user) {}
}
