package com.demandhub.platform.shared.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Authentication cujo principal é o {@link CurrentUser}. Autoridades = permissões + ROLE_papel. */
public class CurrentUserAuthentication extends AbstractAuthenticationToken {

    private final CurrentUser user;
    private final Object credentials;

    public CurrentUserAuthentication(CurrentUser user, Object credentials) {
        super(toAuthorities(user));
        this.user = user;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> toAuthorities(CurrentUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        user.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public CurrentUser getPrincipal() {
        return user;
    }

    @Override
    public String getName() {
        return user.email();
    }
}
