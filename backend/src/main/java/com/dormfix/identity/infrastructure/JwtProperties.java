package com.dormfix.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "dormfix.security.jwt")
record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String keyId,
        @NotBlank String publicKeyBase64,
        @NotBlank String privateKeyBase64) {
}
