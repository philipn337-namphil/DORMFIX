package com.dormfix.identity.infrastructure;

import com.dormfix.identity.application.AccessTokenIssuer;
import com.dormfix.identity.application.IssuedAccessToken;
import com.dormfix.identity.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

final class JwtAccessTokenIssuer implements AccessTokenIssuer {
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final String keyId;

    JwtAccessTokenIssuer(JwtEncoder encoder, String issuer, String audience, String keyId) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.keyId = keyId;
    }

    @Override
    public IssuedAccessToken issue(Long userId, Set<Role> roles, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_LIFETIME);
        List<String> roleNames = roles.stream().map(Role::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roleNames)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }
}
