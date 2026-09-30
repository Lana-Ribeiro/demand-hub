package com.demandhub.platform.identity.service;

import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.identity.domain.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Emissão/validação de JWT HS256. O segredo vem de JWT_SECRET; fora de produção, se ausente,
 * é gerado aleatoriamente a cada inicialização (tokens invalidados no restart).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    public static final String ISSUER = "demand-hub";

    private final SecretKey key;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties props;
    private final Clock clock;

    public JwtService(AppProperties props, Environment env, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.key = resolveKey(props.security().jwtSecret(), env);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKey resolveKey(String secret, Environment env) {
        if (secret == null || secret.isBlank()) {
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("JWT_SECRET é obrigatório no perfil prod.");
            }
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            log.warn("JWT_SECRET não configurado: usando segredo aleatório temporário (somente desenvolvimento).");
            return new SecretKeySpec(random, "HmacSHA256");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET deve ter pelo menos 32 bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant exp = now.plus(props.security().jwtTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(exp)
                .claim("email", user.getEmail())
                .claim("jti", Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, exp);
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        return b;
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
