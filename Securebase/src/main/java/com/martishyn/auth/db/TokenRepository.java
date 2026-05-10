package com.martishyn.auth.db;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TokenRepository extends CrudRepository<Token, Long> {

    List<Token> findByUserId(Long userId);

    Token findByTokenHashLike(String tokenHash);
}
