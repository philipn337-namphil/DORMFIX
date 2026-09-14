package com.dormfix.identity.application;

import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SignupService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public SignupResult signup(SignupCommand command) {
        String normalizedEmail = command.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        Instant now = clock.instant();
        User saved = users.save(new User(normalizedEmail, passwordEncoder.encode(command.password()),
                command.name(), command.phone(), command.studentNumber(), UserStatus.ACTIVE,
                Set.of(Role.RESIDENT), now, now));
        return new SignupResult(saved.getId(), saved.getEmail(), saved.getName(), saved.getPhone(),
                saved.getStudentNumber(), saved.getStatus().name(),
                Set.of(Role.RESIDENT.name()), saved.getCreatedAt());
    }
}
