package com.martishyn.auth;

import com.martishyn.auth.jwt.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final UserAuthService userAuthService;
    private final JwtService jwtService;

    public AuthController(UserAuthService userAuthService, JwtService jwtService) {
        this.userAuthService = userAuthService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerNewUser(@RequestBody RegisterNewUserDto registerNewUserDto) {
        userAuthService.registerNewUser(registerNewUserDto);
        UriComponents location = UriComponentsBuilder.fromPath("api/v1/registrations").build();
        return ResponseEntity.created(location.toUri()).build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> authUser(@RequestBody LoginUserDto loginUserDto) {
        final String jwtToken = userAuthService.loginUser(loginUserDto);
        return ResponseEntity.ok(jwtToken);
    }
}
