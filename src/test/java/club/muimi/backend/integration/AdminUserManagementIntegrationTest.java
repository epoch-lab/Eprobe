package club.muimi.backend.integration;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.auth.LoginRequest;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.UnauthorizedException;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class AdminUserManagementIntegrationTest {

    private final List<String> createdEmails = new ArrayList<>();
    private final List<Long> createdGroupIds = new ArrayList<>();
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecruitmentGroupRepository recruitmentGroupRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanUp() {
        for (Long groupId : createdGroupIds) {
            recruitmentGroupRepository.findById(groupId).ifPresent(recruitmentGroupRepository::delete);
        }
        createdGroupIds.clear();
        for (String email : createdEmails) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
        createdEmails.clear();
    }

    @Test
    void adminEndpointsShouldHidePasswordAndAllowDisablingUser() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminEmail = "admin_manage_" + suffix + "@example.com";
        String userEmail = "managed_user_" + suffix + "@example.com";
        String targetPassword = "UserPass123";
        createdEmails.add(adminEmail);
        createdEmails.add(userEmail);

        userRepository.save(User.builder()
                .username("admin_manage_" + suffix)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode("AdminPass123"))
                .emailVerified(true)
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .lastLoginAt(LocalDateTime.now())
                .build());
        User targetUser = userRepository.save(User.builder()
                .username("managed_user_" + suffix)
                .email(userEmail)
                .passwordHash(passwordEncoder.encode(targetPassword))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .lastLoginAt(LocalDateTime.now())
                .build());
        String originalPasswordHash = targetUser.getPasswordHash();

        AuthCookies authCookies = loginAs(adminEmail, "AdminPass123");

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", targetUser.getUsername())
                        .param("page", "1")
                        .param("size", "10")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(targetUser.getId()))
                .andExpect(jsonPath("$.data.list[0].email").value(userEmail))
                .andExpect(jsonPath("$.data.list[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/users/{userId}", targetUser.getId())
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(targetUser.getId()))
                .andExpect(jsonPath("$.data.email").value(userEmail))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        Long originalTokenVersion = userRepository.findById(targetUser.getId()).orElseThrow().getTokenVersion();
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", targetUser.getId())
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "LEADER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("LEADER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User promotedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(promotedUser.getRole()).isEqualTo(Role.LEADER);
        assertThat(promotedUser.getTokenVersion()).isEqualTo(originalTokenVersion + 1);

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", targetUser.getId())
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User disabledUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(disabledUser.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(disabledUser.getPasswordHash()).isEqualTo(originalPasswordHash);

        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        loginRequest.setRemoteAddr("127.0.0.1");
        assertThatThrownBy(() -> authService.login(
                new LoginRequest(userEmail, targetPassword, false),
                loginRequest,
                new MockHttpServletResponse()
        )).isInstanceOf(UnauthorizedException.class)
                .hasMessage("邮箱或密码错误");
    }

    @Test
    void adminEndpointsShouldStayStableWhenLeaderOwnsMultipleGroups() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminEmail = "admin_multi_" + suffix + "@example.com";
        String leaderEmail = "leader_multi_" + suffix + "@example.com";
        createdEmails.add(adminEmail);
        createdEmails.add(leaderEmail);

        userRepository.save(User.builder()
                .username("admin_multi_" + suffix)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode("AdminPass123"))
                .emailVerified(true)
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .lastLoginAt(LocalDateTime.now())
                .build());
        User leader = userRepository.save(User.builder()
                .username("leader_multi_" + suffix)
                .email(leaderEmail)
                .passwordHash(passwordEncoder.encode("LeaderPass123"))
                .emailVerified(true)
                .role(Role.LEADER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .lastLoginAt(LocalDateTime.now())
                .build());

        RecruitmentGroup firstGroup = recruitmentGroupRepository.save(RecruitmentGroup.builder()
                .name("group_multi_a_" + suffix)
                .directionLevel1Id(1L)
                .directionLevel2Id(2L)
                .grade(club.muimi.backend.common.enums.Grade.YEAR_1)
                .admissionYear(2026)
                .maxSize(20)
                .leaderUserId(leader.getId())
                .build());
        RecruitmentGroup secondGroup = recruitmentGroupRepository.save(RecruitmentGroup.builder()
                .name("group_multi_b_" + suffix)
                .directionLevel1Id(1L)
                .directionLevel2Id(2L)
                .grade(club.muimi.backend.common.enums.Grade.YEAR_1)
                .admissionYear(2026)
                .maxSize(20)
                .leaderUserId(leader.getId())
                .build());
        createdGroupIds.add(firstGroup.getId());
        createdGroupIds.add(secondGroup.getId());

        AuthCookies authCookies = loginAs(adminEmail, "AdminPass123");

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", leader.getUsername())
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(leader.getId()))
                .andExpect(jsonPath("$.data.list[0].leaderGroupCount").value(2));

        mockMvc.perform(get("/api/v1/admin/users/{userId}", leader.getId())
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(leader.getId()))
                .andExpect(jsonPath("$.data.leaderGroups.length()").value(2))
                .andExpect(jsonPath("$.data.leaderGroups[*].id").value(containsInAnyOrder(
                        firstGroup.getId().intValue(),
                        secondGroup.getId().intValue()
                )));

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", leader.getId())
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "FRESHMAN"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该负责人仍绑定负责的分组，不能降级为新生"));
    }

    private AuthCookies loginAs(String email, String password) throws IOException {
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        loginRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        authService.login(new LoginRequest(email, password, false), loginRequest, loginResponse);
        return new AuthCookies(
                extractCookie(loginResponse, "lab_recruit_token"),
                extractCookie(loginResponse, "XSRF-TOKEN")
        );
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

    private record AuthCookies(Cookie authCookie, Cookie csrfCookie) {
    }
}
