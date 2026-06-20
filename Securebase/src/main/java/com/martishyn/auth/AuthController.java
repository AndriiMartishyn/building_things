package com.martishyn.auth;

import com.martishyn.auth.jwt.JwtPairDto;
import com.martishyn.auth.redis.RedisRateLimiter;
import com.martishyn.auth.redis.TooManyRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final UserAuthService userAuthService;
    private final ObjectMapper objectMapper;
    @Autowired
    private final RedisRateLimiter redisRateLimiter;

    public AuthController(UserAuthService userAuthService, ObjectMapper objectMapper, RedisRateLimiter redisRateLimiter) {
        this.userAuthService = userAuthService;
        this.objectMapper = objectMapper;
        this.redisRateLimiter = redisRateLimiter;
        this.objectMapper.serializationConfig().constructDefaultPrettyPrinter();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerNewUser(@RequestBody RegisterNewUserDto registerNewUserDto) {
        userAuthService.registerNewUser(registerNewUserDto);
        UriComponents location = UriComponentsBuilder.fromPath("api/v1/registrations").build();
        return ResponseEntity.created(location.toUri()).build();
    }

    //FUNCTION LIMIT_API_CALL(ip)
    //ts = CURRENT_UNIX_TIME()
    //keyname = ip+":"+ts
    //MULTI
    //    INCR(keyname)
    //    EXPIRE(keyname,10)
    //EXEC
    //current = RESPONSE_OF_INCR_WITHIN_MULTI
    //IF current > 10 THEN
    //    ERROR "too many requests per second"
    //ELSE
    //    PERFORM_API_CALL()
    //END
    @PostMapping("/login")
    public ResponseEntity<?> authUser(@RequestBody LoginUserDto loginUserDto, HttpServletRequest request) throws TooManyRequestException {
        final boolean isAllowedRequest = redisRateLimiter.isAllowedRequest(request.getRemoteAddr());
        if (!isAllowedRequest) {
            throw new TooManyRequestException("Too many requests from 1 user!");
        }
        final JwtPairDto tokensPair = userAuthService.loginUser(loginUserDto);
        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokensPair.refreshToken())
                .httpOnly(true) //xss
                .secure(false) //true for https
                .path("/api/v1/refresh") //use only for this path
                .sameSite("Strict")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(tokensPair.accessToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(@CookieValue(value = "refreshToken", required = false) String refreshToken) {
        if (refreshToken != null) {
            userAuthService.logoutUser(refreshToken);
        }
        System.err.println("logoutUser");
        //removing cookie from browser
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("strict")
                .path("/api/v1/refresh")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshTokens(@CookieValue("refreshToken") String refreshToken) {
        final Optional<JwtPairDto> jwtPairDto = userAuthService.issueNewRefreshToken(refreshToken);
        final Optional<ResponseCookie> responseCookie = jwtPairDto.map(pair -> ResponseCookie.from("refreshToken", jwtPairDto.get().refreshToken())
                .path("/api/v1/refresh")
                .secure(false)
                .httpOnly(true)
                .sameSite("Strict")
                .build());
        return responseCookie.map(cookie ->
                        ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(jwtPairDto.get().accessToken()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials passed"));
    }
}
