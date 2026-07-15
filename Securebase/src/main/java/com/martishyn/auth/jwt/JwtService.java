package com.martishyn.auth.jwt;

import com.martishyn.auth.db.Role;
import com.martishyn.auth.db.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private static final String JWT_SECRET = "myTopSecretmyTopSecretmyTopSecretmyTopSecretmyTopSecretmyTopSecret";
    private static final String JWT_ISSUER = "martishyn";

    public String issueAccessToken(User userByEmail) {
        return Jwts.builder()
                .subject(userByEmail.getEmail())
                .claim("roles", userByEmail.getRoles().stream().map(Role::getRoleName).toList())
                .issuedAt(new Date(System.currentTimeMillis()))
                .issuer(JWT_ISSUER)
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
                .signWith(getEncryptedKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String issueRefreshToken(User userByEmail) {
        return Jwts.builder()
                .subject(userByEmail.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .issuer(JWT_ISSUER)
                .expiration(Date.from(Instant.now().plus(5, ChronoUnit.DAYS)))
                .signWith(getEncryptedKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractTokenClaims(String token) {
        final Claims tokenClaims = Jwts.parser()
                .verifyWith(getEncryptedKey())
                .requireIssuer(JWT_ISSUER)
                .build()
                .parseSignedClaims(token).accept(Jws.CLAIMS)
                .getPayload();
        return tokenClaims;
    }


    private SecretKey getEncryptedKey() {
        byte[] encodedSecret = Base64.getDecoder().decode(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Keys.hmacShaKeyFor(encodedSecret);
    }

}
