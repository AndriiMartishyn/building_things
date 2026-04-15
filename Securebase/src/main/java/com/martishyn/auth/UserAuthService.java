package com.martishyn.auth;

import com.martishyn.auth.db.User;
import com.martishyn.auth.db.UserAuthRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.List;

@Service
public class UserAuthService {

    private final UserAuthRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAuthService(UserAuthRepository userAuthRepository, PasswordEncoder passwordEncoder) {
        this.userAuthRepository = userAuthRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void registerNewUser(RegisterNewUserDto registerNewUserDto){
        String encodedPassword = passwordEncoder.encode(registerNewUserDto.password());
        User user = new User(registerNewUserDto.email(), encodedPassword);
        userAuthRepository.save(user);
    }
}
