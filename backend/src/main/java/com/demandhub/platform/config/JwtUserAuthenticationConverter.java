package com.demandhub.platform.config;

import com.demandhub.platform.identity.domain.User;
import com.demandhub.platform.identity.repository.UserRepository;
import com.demandhub.platform.shared.security.CurrentUserAuthentication;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Converte o JWT em {@link CurrentUserAuthentication} carregando usuário, papéis e permissões do banco a cada requisição:
 * usuário desativado ou com papéis alterados tem o efeito aplicado imediatamente.
 */
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;
    private final TransactionTemplate tx;

    public JwtUserAuthenticationConverter(UserRepository users, TransactionTemplate tx) {
        this.users = users;
        this.tx = tx;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return tx.execute(status -> {
            UUID id;
            try {
                id = UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new InvalidBearerTokenException("Token inválido");
            }
            User user = users.findById(id).filter(User::isActive)
                    .orElseThrow(() -> new InvalidBearerTokenException("Usuário inválido ou inativo"));
            return new CurrentUserAuthentication(user.toCurrentUser(), jwt);
        });
    }
}
