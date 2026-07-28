package club.muimi.backend.integration;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.auth.LoginRequest;
import club.muimi.backend.entity.User;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.jwt.JwtAuthenticationFilter;
import club.muimi.backend.service.auth.AuthService;
import club.muimi.backend.support.redis.AuthCacheService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/fresh?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&sessionVariables=default_storage_engine=InnoDB",
        "spring.datasource.username=epoch",
        "spring.datasource.password=123456",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "spring.mail.username=test-mail@example.com",
        "spring.mail.password=test-auth-code",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "app.security.jwt.secret=test-jwt-secret-key-with-at-least-32-bytes-long",
        "app.security.jwt.cookie-secure=false",
        "app.security.jwt.cookie-same-site=Lax",
        "app.auth.cache-type=redis",
        "app.bootstrap.default-admin.enabled=false"
})
class AuthFlowRedisIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private AuthCacheService authCacheService;

    private String createdEmail;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        if (createdEmail != null) {
            userRepository.findByEmail(createdEmail).ifPresent(userRepository::delete);
            createdEmail = null;
        }
    }

    @Test
    void loginAndCurrentUserShouldWorkWhenAuthCacheUsesRedis() throws Exception {
        assertThat(authCacheService.getClass().getSimpleName()).isEqualTo("RedisAuthCacheService");

        String suffix = String.valueOf(System.nanoTime());
        createdEmail = "redis_flow_" + suffix + "@example.com";
        String username = "redis_flow_" + suffix;
        String password = "RedisPass123";

        userRepository.save(User.builder()
                .username(username)
                .email(createdEmail)
                .passwordHash(passwordEncoder.encode(password))
                .emailVerified(true)
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .lastLoginAt(LocalDateTime.now())
                .build());

        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        loginRequest.setRemoteAddr("127.0.0.1");
        authService.login(new LoginRequest(createdEmail, password, false), loginRequest, loginResponse);

        Cookie authCookie = extractCookie(loginResponse, "lab_recruit_token");
        Cookie csrfCookie = extractCookie(loginResponse, "XSRF-TOKEN");

        SecurityContextHolder.clearContext();
        MockHttpServletRequest authenticatedRequest = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        authenticatedRequest.setCookies(authCookie, csrfCookie);
        jwtAuthenticationFilter.doFilter(authenticatedRequest, new MockHttpServletResponse(), new MockFilterChain());

        var currentUser = authService.getCurrentUser();
        assertThat(currentUser.email()).isEqualTo(createdEmail);
        assertThat(currentUser.role()).isEqualTo(Role.ADMIN);
        assertThat(currentUser.groups()).isEmpty();
    }

    private Cookie extractCookie(MockHttpServletResponse response, String cookieName) throws IOException {
        for (String header : response.getHeaders("Set-Cookie")) {
            if (header.startsWith(cookieName + "=")) {
                String value = header.substring(cookieName.length() + 1, header.indexOf(';')).replace("\"", "");
                return new Cookie(cookieName, value);
            }
        }
        throw new IOException("缺少 Cookie: " + cookieName);
    }
}
