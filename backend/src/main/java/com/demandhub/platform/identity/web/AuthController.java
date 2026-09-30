package com.demandhub.platform.identity.web;

import com.demandhub.platform.identity.service.UserService;
import com.demandhub.platform.identity.web.UserDtos.LoginRequest;
import com.demandhub.platform.identity.web.UserDtos.LoginResponse;
import com.demandhub.platform.identity.web.UserDtos.UserResponse;
import com.demandhub.platform.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserService.LoginResult result = userService.login(request.email(), request.password());
        return new LoginResponse(result.token().token(), result.token().expiresAt(), UserResponse.from(result.user()));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(userService.get(SecurityUtils.currentUser().id()));
    }
}
