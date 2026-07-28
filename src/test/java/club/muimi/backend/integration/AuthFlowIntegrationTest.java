package club.muimi.backend.integration;

import club.muimi.backend.common.enums.EmailCodeScene;
import club.muimi.backend.common.enums.PeriodType;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.auth.LoginRequest;
import club.muimi.backend.dto.auth.RegisterRequest;
import club.muimi.backend.entity.RecruitmentPeriod;
import club.muimi.backend.entity.User;
import club.muimi.backend.repository.RecruitmentPeriodRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.jwt.JwtAuthenticationFilter;
import club.muimi.backend.security.jwt.JwtClaims;
import club.muimi.backend.security.jwt.JwtTokenService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
class AuthFlowIntegrationTest {

    private final List<String> createdEmails = new ArrayList<>();
    @Autowired
    private AuthService authService;
    @Autowired
    private AuthCacheService authCacheService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecruitmentPeriodRepository recruitmentPeriodRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired
    private JwtTokenService jwtTokenService;
    private RecruitmentPeriod originalRegistrationPeriodSnapshot;
    private Long managedRegistrationPeriodId;
    private boolean createdRegistrationPeriodForTest;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (String email : createdEmails) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
        createdEmails.clear();
        restoreRegistrationPeriod();
    }

    @Test
    void registerLoginJwtAuthenticateLogoutChainShouldWork() throws Exception {
        openRegistrationPeriod();
        String suffix = String.valueOf(System.nanoTime());
        String username = "flow_user_" + suffix;
        String email = "flow_" + suffix + "@example.com";
        String password = "Pass1234";
        createdEmails.add(email);

        // 集成测试直接预置验证码，避免依赖真实 SMTP 服务。
        authCacheService.saveEmailCode(EmailCodeScene.REGISTER, email, "123456", Duration.ofMinutes(5));

        authService.register(new RegisterRequest(
                username,
                email,
                password,
                password,
                "123456"
        ));

        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        loginRequest.setRemoteAddr("127.0.0.1");
        authService.login(new LoginRequest(email, password, false), loginRequest, loginResponse);

        Cookie authCookie = extractCookie(loginResponse, "lab_recruit_token");
        Cookie csrfCookie = extractCookie(loginResponse, "XSRF-TOKEN");

        authenticateRequestWithJwt(authCookie, csrfCookie);
        var currentUser = authService.getCurrentUser();
        assertThat(currentUser.email()).isEqualTo(email);
        assertThat(currentUser.role()).isEqualTo(Role.FRESHMAN);
        assertThat(currentUser.groups()).isEmpty();

        JwtClaims claims = jwtTokenService.parse(authCookie.getValue());
        MockHttpServletRequest logoutRequest = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        logoutRequest.setCookies(authCookie, csrfCookie);
        authService.logout(logoutRequest, new MockHttpServletResponse());
        assertThat(authCacheService.isTokenBlacklisted(claims.jti())).isTrue();

        SecurityContextHolder.clearContext();
        MockHttpServletRequest requestAfterLogout = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        requestAfterLogout.setCookies(authCookie, csrfCookie);
        MockHttpServletResponse responseAfterLogout = new MockHttpServletResponse();
        jwtAuthenticationFilter.doFilter(requestAfterLogout, responseAfterLogout, new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void adminWithoutGroupShouldLoginAndReadCurrentUserSuccessfully() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String email = "admin_flow_" + suffix + "@example.com";
        String username = "admin_flow_" + suffix;
        String password = "AdminPass123";
        createdEmails.add(email);

        userRepository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .emailVerified(true)
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                // 这里故意不创建任何小组关系，用来验证管理员空分组链路不会再触发 500。
                .lastLoginAt(LocalDateTime.now())
                .build());

        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        loginRequest.setRemoteAddr("127.0.0.1");
        authService.login(new LoginRequest(email, password, false), loginRequest, loginResponse);

        Cookie authCookie = extractCookie(loginResponse, "lab_recruit_token");
        Cookie csrfCookie = extractCookie(loginResponse, "XSRF-TOKEN");

        authenticateRequestWithJwt(authCookie, csrfCookie);
        var currentUser = authService.getCurrentUser();
        assertThat(currentUser.email()).isEqualTo(email);
        assertThat(currentUser.role()).isEqualTo(Role.ADMIN);
        assertThat(currentUser.groups()).isEmpty();
    }

    private void authenticateRequestWithJwt(Cookie authCookie, Cookie csrfCookie) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest authenticatedRequest = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        authenticatedRequest.setCookies(authCookie, csrfCookie);
        jwtAuthenticationFilter.doFilter(authenticatedRequest, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
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

    private void openRegistrationPeriod() {
        RecruitmentPeriod period = recruitmentPeriodRepository.findAll()
                .stream()
                .filter(item -> item.getPeriodType() == PeriodType.REGISTRATION)
                .findFirst()
                .orElse(null);

        if (period == null) {
            RecruitmentPeriod created = recruitmentPeriodRepository.save(RecruitmentPeriod.builder()
                    .periodType(PeriodType.REGISTRATION)
                    .startTime(LocalDateTime.now().minusDays(1))
                    .endTime(LocalDateTime.now().plusDays(1))
                    .enabled(true)
                    .build());
            managedRegistrationPeriodId = created.getId();
            createdRegistrationPeriodForTest = true;
            return;
        }

        managedRegistrationPeriodId = period.getId();
        createdRegistrationPeriodForTest = false;
        originalRegistrationPeriodSnapshot = RecruitmentPeriod.builder()
                .id(period.getId())
                .periodType(period.getPeriodType())
                .startTime(period.getStartTime())
                .endTime(period.getEndTime())
                .enabled(period.getEnabled())
                .createdAt(period.getCreatedAt())
                .updatedAt(period.getUpdatedAt())
                .build();
        period.setEnabled(true);
        period.setStartTime(LocalDateTime.now().minusDays(1));
        period.setEndTime(LocalDateTime.now().plusDays(1));
        recruitmentPeriodRepository.save(period);
    }

    private void restoreRegistrationPeriod() {
        if (managedRegistrationPeriodId == null) {
            return;
        }

        if (createdRegistrationPeriodForTest) {
            recruitmentPeriodRepository.deleteById(managedRegistrationPeriodId);
        } else if (originalRegistrationPeriodSnapshot != null) {
            recruitmentPeriodRepository.findById(managedRegistrationPeriodId).ifPresent(period -> {
                period.setEnabled(originalRegistrationPeriodSnapshot.getEnabled());
                period.setStartTime(originalRegistrationPeriodSnapshot.getStartTime());
                period.setEndTime(originalRegistrationPeriodSnapshot.getEndTime());
                recruitmentPeriodRepository.save(period);
            });
        }

        managedRegistrationPeriodId = null;
        createdRegistrationPeriodForTest = false;
        originalRegistrationPeriodSnapshot = null;
    }
}
