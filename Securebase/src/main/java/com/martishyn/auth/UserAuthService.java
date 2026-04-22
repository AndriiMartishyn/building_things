package com.martishyn.auth;

import com.martishyn.auth.db.User;
import com.martishyn.auth.db.UserAuthRepository;
import com.martishyn.auth.jwt.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuthService {

    private final UserAuthRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserAuthService(UserAuthRepository userAuthRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userAuthRepository = userAuthRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public void registerNewUser(RegisterNewUserDto registerNewUserDto){
        String encodedPassword = passwordEncoder.encode(registerNewUserDto.password());
        User user = new User(registerNewUserDto.email(), encodedPassword);
        userAuthRepository.save(user);
    }

    public String loginUser(LoginUserDto loginUserDto){
        final User userByEmail = userAuthRepository.findUserByEmail(loginUserDto.email())
                .orElseThrow(() -> new RuntimeException("User not found"));
        final String enteredPassword = loginUserDto.password();
        final String encodedPassword = passwordEncoder.encode(enteredPassword);
        if (!encodedPassword.equals(encodedPassword)) {
            throw new RuntimeException("Passwords do not match");
        }
        final String jwtToken = jwtService.issueToken(userByEmail);
        return jwtToken;
    }
}
