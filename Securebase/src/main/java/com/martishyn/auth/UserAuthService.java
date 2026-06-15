package com.martishyn.auth;

import com.martishyn.auth.db.Role;
import com.martishyn.auth.db.RoleRepository;
import com.martishyn.auth.db.Token;
import com.martishyn.auth.db.TokenRepository;
import com.martishyn.auth.db.User;
import com.martishyn.auth.db.UserAuthRepository;
import com.martishyn.auth.jwt.JwtPairDto;
import com.martishyn.auth.jwt.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class UserAuthService {

    private final UserAuthRepository userAuthRepository;
    private final RoleRepository roleRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public UserAuthService(UserAuthRepository userAuthRepository,
                           RoleRepository roleRepository,
                           TokenRepository tokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userAuthRepository = userAuthRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.serializationConfig().constructDefaultPrettyPrinter();
    }

    @Transactional
    public void registerNewUser(RegisterNewUserDto registerNewUserDto){
        String encodedPassword = passwordEncoder.encode(registerNewUserDto.password());
        User user = new User(registerNewUserDto.email(), encodedPassword);
        Role customerRole = roleRepository.findRoleByRoleName("ROLE_CUSTOMER");
        user.addRole(customerRole);
        userAuthRepository.save(user);
    }

    @Transactional
    public JwtPairDto loginUser(LoginUserDto loginUserDto){
        final User userByEmail = userAuthRepository.findUserByEmail(loginUserDto.email())
                .orElseThrow(() -> new RuntimeException("User not found"));
        final String enteredPassword = loginUserDto.password();
        if (!passwordEncoder.matches(enteredPassword,userByEmail.getPassword())) {
            throw new RuntimeException("Passwords do not match");
        }
        final String accessToken = jwtService.issueAccessToken(userByEmail);
        final String refreshToken = jwtService.issueRefreshToken(userByEmail);
        JwtPairDto jwtPairDto = new JwtPairDto(accessToken, refreshToken);

        final String hashToken = hashIssuedToken(refreshToken);
        Token createdRefreshToken = new Token(hashToken, LocalDateTime.now().plusDays(7), userByEmail);
        tokenRepository.save(createdRefreshToken);
        return jwtPairDto;
    }

    public void logoutUser(String refreshToken) {
        final String hashedRefreshToken = hashIssuedToken(refreshToken);
        tokenRepository.findByTokenHashLike(hashedRefreshToken)
                        .ifPresent(token -> {
                            token.setRevoked(true);
                            tokenRepository.save(token);
                        });
    }

    //user has X tokens, f.e. 1 of them is already expired
    // we invoke new token -> add new token to the database with the status active
    // if some of them not revoked -> revoke THEM ???
    @Transactional
    public Optional<JwtPairDto> issueNewRefreshToken(String refreshToken){
        final Claims claims = jwtService.extractTokenClaims(refreshToken);
        final String userEmailFromToken = claims.getSubject();
        final User userByEmail = userAuthRepository.findUserByEmail(userEmailFromToken)
                .orElseThrow(() -> new RuntimeException("User not found"));
        final String hashToken = hashIssuedToken(refreshToken);

        final Optional<Token> existingToken = tokenRepository.findByTokenHashLike(hashToken);
        if (existingToken.isEmpty()) {
            return Optional.empty();
        }
        //probably attacker?
        if (existingToken.get().isRevoked()) {
            final List<Token> userTokens = tokenRepository.findByUserId(userByEmail.getId());
            userTokens.forEach(userToken -> userToken.setRevoked(true));
            tokenRepository.saveAll(userTokens);
            return Optional.empty();
        }
        existingToken.get().setRevoked(true);
        tokenRepository.save(existingToken.get());

        final String accessToken = jwtService.issueAccessToken(userByEmail);
        final String newRefreshToken = jwtService.issueRefreshToken(userByEmail);
        JwtPairDto jwtPairDto = new JwtPairDto(accessToken, newRefreshToken);

        final String hashedRefreshToken = hashIssuedToken(newRefreshToken);
        tokenRepository.save(new Token(hashedRefreshToken, LocalDateTime.now().plusDays(7), userByEmail));
        return Optional.of(jwtPairDto);
    }

    private String hashIssuedToken(String refreshToken) {
        String hashToken;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            final byte[] hashedToken = messageDigest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            hashToken = Base64.getEncoder().encodeToString(hashedToken);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return hashToken;
    }
}
