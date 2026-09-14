package com.dormfix.identity.application;

import com.dormfix.identity.domain.User;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CurrentUserResult findById(Long userId) {
        User user = users.findById(userId).orElseThrow(CurrentUserNotFoundException::new);
        Set<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUserResult(user.getId(), user.getEmail(), user.getName(), user.getPhone(),
                user.getStudentNumber(), user.getStatus().name(), roles, user.getLastLoginAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
