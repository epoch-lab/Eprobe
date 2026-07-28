package club.muimi.backend.integration;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.auth.LoginRequest;
import club.muimi.backend.entity.User;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/fresh?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&sessionVariables=default_storage_engine=InnoDB",
        "spring.datasource.username=epoch",
        "spring.datasource.password=123456",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "spring.mail.username=test-mail@example.com",
        "spring.mail.password=test-auth-code",
        "app.security.jwt.secret=test-jwt-secret-key-with-at-least-32-bytes-long",
        "app.security.jwt.cookie-secure=false",
        "app.security.jwt.cookie-same-site=Lax",
        "app.auth.cache-type=redis",
        "app.bootstrap.default-admin.enabled=false"
})
@WebAppConfiguration
class MissingApiRouteIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String createdEmail;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterEach
    void cleanUp() {
        if (createdEmail != null) {
            userRepository.findByEmail(createdEmail).ifPresent(userRepository::delete);
            createdEmail = null;
        }
    }

    @Test
    void missingApiShouldReturn404InsteadOf500WhenUserLoggedIn() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        createdEmail = "missing_route_" + suffix + "@example.com";
        String username = "missing_route_" + suffix;
        String password = "Missing1234";

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
        org.springframework.mock.web.MockHttpServletRequest loginRequest =
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/auth/login");
        loginRequest.setRemoteAddr("127.0.0.1");
        authService.login(new LoginRequest(createdEmail, password, false), loginRequest, loginResponse);
        Cookie authCookie = extractCookie(loginResponse, "lab_recruit_token");
        Cookie csrfCookie = extractCookie(loginResponse, "XSRF-TOKEN");

        mockMvc.perform(get("/api/v1/not-exist")
                        .cookie(authCookie, csrfCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    void currentUserShouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    private Cookie extractCookie(MockHttpServletResponse response, String cookieName) throws IOException {
        for (String header : response.getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(cookieName + "=")) {
                String value = header.substring(cookieName.length() + 1, header.indexOf(';')).replace("\"", "");
                return new Cookie(cookieName, value);
            }
        }
        throw new IOException("缺少 Cookie: " + cookieName);
    }
}
