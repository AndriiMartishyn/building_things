package com.martishyn;

import com.martishyn.auth.LoginUserDto;
import com.martishyn.auth.RegisterNewUserDto;
import com.martishyn.auth.db.Role;
import com.martishyn.auth.db.RoleRepository;
import com.martishyn.auth.db.User;
import com.martishyn.auth.db.UserAuthRepository;
import com.martishyn.auth.jwt.JwtService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.useRepresentation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@Sql(scripts = "/db/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class AuthEndToEndTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:latest").withExposedPorts(6379);
    @Container
    static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:18.0-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
        registry.add("spring.datasource.driver-class-name", pg::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    @Autowired
    UserAuthRepository users;
    TestRestTemplate restTemplate = new TestRestTemplate();

    @LocalServerPort
    int port;

    @AfterAll
    static void dumpLogs() {
        System.out.println("---REDIS LOGS---");
        System.out.println(redis.getLogs());
    }

    @Test
    public void shouldReturnUnauthorizedWhenAccessingPrivatePage()  {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/v1/dashboard/roles"), String.class);
        assertEquals(response.getStatusCode().value(), HttpStatus.UNAUTHORIZED.value());
    }


    @Test
    public void shouldRegisterNewUser()  {
        RegisterNewUserDto registerNewUserDto = new RegisterNewUserDto("test@gmail.com", "test12345");

        restTemplate.postForEntity(url("/api/v1/register"), registerNewUserDto, String.class);

        Optional<User> registeredUser = users.findOptionalUserByEmail("test@gmail.com");
        assertThat(registeredUser).isPresent();
        assertThat(registeredUser.get()).isNotNull();
        assertThat(registeredUser.get().getEmail()).contains("test@gmail.com");
        assertThat(registeredUser.get().getRoles()).hasSize(1);
    }

    @Test
    public void shouldProceedProtectedRouteWithValidBearer()  {
        RegisterNewUserDto register = new RegisterNewUserDto("test1@gmail.com", "12345");
        ResponseEntity<Void> regResp = restTemplate.postForEntity(url("/api/v1/register"), register, Void.class);
        assertEquals(201, regResp.getStatusCode().value());

        LoginUserDto login = new LoginUserDto("test1@gmail.com", "12345");
        ResponseEntity<String> loginResp = restTemplate.postForEntity(
                url("/api/v1/login"), login, String.class);
        assertEquals(200, loginResp.getStatusCode().value());
        String accessToken = loginResp.getBody();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(accessToken);
        ResponseEntity<String> protectedResp = restTemplate.exchange(
                url("/api/v1/dashboard/roles"), HttpMethod.GET,
                new HttpEntity<>(h), String.class);

        assertEquals(200, protectedResp.getStatusCode().value());
        assertThat(protectedResp.getBody()).contains("ROLE_CUSTOMER");
}

    @Test
    public void shouldProceedExistingUser(){
        final Optional<User> user = users.findUserByEmail("test@gmail.com");
        LoginUserDto loginUserDto = new LoginUserDto(user.get().getEmail(),  "test12345");
        final ResponseEntity<String> response = restTemplate.postForEntity(url("/api/v1/login"), loginUserDto, String.class);

        assertEquals(HttpStatus.OK.value(),response.getStatusCode().value());
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("refreshToken=")
                .contains("Path=/api/v1/refresh");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
