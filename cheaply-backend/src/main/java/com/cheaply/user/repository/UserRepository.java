package com.cheaply.user.repository;

import com.cheaply.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Lookups are case-insensitive throughout.
 *
 * <p>Usernames and emails are stored as the user typed them but compared
 * without regard to case, matching the functional unique indexes in
 * {@code V1__baseline_schema.sql}. A case-sensitive lookup against a
 * case-insensitive uniqueness rule is how "Admin" ends up unable to log in
 * while also being unable to register.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);
}
