package com.martishyn.auth.db;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends CrudRepository<Token, Long> {

    List<Token> findByUserId(Long userId);

    Optional<Token> findByTokenHashLike(String tokenHash);
}
