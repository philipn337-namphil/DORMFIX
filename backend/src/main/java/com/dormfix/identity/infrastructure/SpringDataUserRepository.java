package com.dormfix.identity.infrastructure;

import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.UserStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dormfix.identity.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u from User u where (:role is null or :role member of u.roles)"
            + " and (:status is null or u.status = :status) and (:email is null or u.email = :email)"
            + " order by u.name asc, u.id asc")
    List<User> search(@Param("role") Role role, @Param("status") UserStatus status,
            @Param("email") String email, Pageable pageable);

    @Query("select count(u) from User u where (:role is null or :role member of u.roles)"
            + " and (:status is null or u.status = :status) and (:email is null or u.email = :email)")
    long countSearch(@Param("role") Role role, @Param("status") UserStatus status,
            @Param("email") String email);
}
