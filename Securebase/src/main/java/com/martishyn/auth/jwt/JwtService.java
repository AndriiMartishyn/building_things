package com.martishyn.auth.jwt;

import com.martishyn.auth.db.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecureDigestAlgorithm;
import io.jsonwebtoken.security.SecureRequest;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.security.VerifySecureDigestRequest;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private static final String JWT_SECRET = "myTopSecretmyTopSecretmyTopSecretmyTopSecretmyTopSecretmyTopSecret";
    private static final String JWT_ISSUER = "martishyn";
    private static final long JWT_EXPIRATION_30_M = 1799997;

    public String issueToken(User userByEmail) {
        return Jwts.builder()
                .subject(userByEmail.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .issuer(JWT_ISSUER)
                .expiration(new Date(JWT_EXPIRATION_30_M))
                .signWith(getEncryptedKey(), Jwts.SIG.HS256)
                .compact();
    }

    //(K key, final SecureDigestAlgorithm<? super K, ?> alg

    public Claims extractTokenClaims(String token) {
        final Claims tokenClaims = Jwts.parser()
                .verifyWith(getEncryptedKey())
                .requireIssuer(JWT_ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return tokenClaims;
    }

    private SecretKey getEncryptedKey(){
        byte[] encodedSecret = Base64.getDecoder()
                .decode(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
       return Keys.hmacShaKeyFor(encodedSecret);
    }
}
