package com.dormfix.identity.application;

import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");

    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    private SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(users, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void normalizesEmailHashesPasswordAndAssignsResidentActiveDefaults() {
        when(users.existsByEmail("resident@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-secret")).thenReturn("encoded-secret");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignupResult result = signupService.signup(new SignupCommand(
                "Resident", "  Resident@Example.COM ", "plain-secret", "010-0000-0000", "20260001"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("resident@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-secret");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getRoles()).containsExactly(Role.RESIDENT);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result.email()).isEqualTo("resident@example.com");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.roles()).containsExactly("RESIDENT");
    }

    @Test
    void rejectsNormalizedDuplicateBeforeEncodingOrSaving() {
        when(users.existsByEmail("resident@example.com")).thenReturn(true);

        assertThatThrownBy(() -> signupService.signup(new SignupCommand(
                "Resident", " Resident@Example.COM ", "plain-secret", null, null)))
                .isInstanceOf(DuplicateEmailException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(users, never()).save(any());
    }
}
