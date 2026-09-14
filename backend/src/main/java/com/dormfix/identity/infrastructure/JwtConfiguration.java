package com.dormfix.identity.infrastructure;

import com.dormfix.identity.application.AccessTokenIssuer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfiguration {
    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAPublicKey publicKey = publicKey(properties.publicKeyBase64());
        RSAPrivateKey privateKey = privateKey(properties.privateKeyBase64());
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("JWT RSA public and private keys do not match.");
        }
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.keyId())
                .algorithm(JWSAlgorithm.RS256)
                .build();
        JWKSource<SecurityContext> keys = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(keys);
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        return new JwtAccessTokenIssuer(encoder, properties.issuer(), properties.audience(), properties.keyId());
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        RSAPublicKey publicKey = publicKey(properties.publicKeyBase64());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "The access token audience is invalid.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, audience));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> (org.springframework.security.core.GrantedAuthority)
                            new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });
        return converter;
    }

    private RSAPublicKey publicKey(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("JWT public-key configuration is invalid.", error);
        }
    }

    private RSAPrivateKey privateKey(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("JWT private-key configuration is invalid.", error);
        }
    }
}
