package com.martishyn.auth.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserAuthRepository extends JpaRepository<User, Long> {

    @Query("select u FROM User as u JOIN FETCH u.roles WHERE u.email = :email ")
    Optional<User> findOptionalUserByEmail(String email);

    Optional<User> findUserByEmail(String email);
}
