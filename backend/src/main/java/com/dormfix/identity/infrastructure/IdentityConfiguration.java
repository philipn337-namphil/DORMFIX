package com.dormfix.identity.infrastructure;

import java.time.Clock;
import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom refreshTokenRandom() {
        return new SecureRandom();
    }
}
