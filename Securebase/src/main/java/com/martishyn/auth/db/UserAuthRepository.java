package com.martishyn.auth.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAuthRepository extends JpaRepository<User, Long> {
    Optional<User> findOptionalUserByEmail(String email);

    Optional<User> findUserByEmail(String email);
}
