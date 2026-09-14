package com.dormfix.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class TestJwtKeys {
    public static final String ISSUER = "https://issuer.dormfix.test";
    public static final String AUDIENCE = "dormfix-api-test";
    public static final String KEY_ID = "integration-key";

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private TestJwtKeys() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("dormfix.security.jwt.issuer", () -> ISSUER);
        registry.add("dormfix.security.jwt.audience", () -> AUDIENCE);
        registry.add("dormfix.security.jwt.key-id", () -> KEY_ID);
        registry.add("dormfix.security.jwt.public-key-base64",
                () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
        registry.add("dormfix.security.jwt.private-key-base64",
                () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded()));
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    public static RSAPrivateKey privateKey() {
        return (RSAPrivateKey) KEY_PAIR.getPrivate();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("RSA is unavailable for integration tests.", error);
        }
    }
}
