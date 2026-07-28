package club.muimi.backend.integration;

import club.muimi.backend.common.enums.PeriodType;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.auth.LoginRequest;
import club.muimi.backend.entity.RecruitmentPeriod;
import club.muimi.backend.entity.User;
import club.muimi.backend.repository.DirectionRepository;
import club.muimi.backend.repository.RecruitmentPeriodRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.service.auth.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
class AdminConfigIntegrationTest {

    private final List<String> createdEmails = new ArrayList<>();
    private final List<Long> createdDirectionIds = new ArrayList<>();
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DirectionRepository directionRepository;
    @Autowired
    private RecruitmentPeriodRepository recruitmentPeriodRepository;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;
    private List<RecruitmentPeriod> periodSnapshots;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        periodSnapshots = recruitmentPeriodRepository.findAll().stream()
                .map(this::copyPeriod)
                .toList();
    }

    @AfterEach
    void cleanUp() {
        for (int i = createdDirectionIds.size() - 1; i >= 0; i--) {
            Long directionId = createdDirectionIds.get(i);
            directionRepository.findById(directionId).ifPresent(directionRepository::delete);
        }
        createdDirectionIds.clear();
        for (String email : createdEmails) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
        createdEmails.clear();
        restorePeriods();
    }

    @Test
    void adminShouldManageDirections() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        User admin = createAdminUser(suffix);
        AuthCookies authCookies = loginAs(admin.getEmail(), "AdminPass123");

        long rootId = extractId(mockMvc.perform(post("/api/v1/admin/directions")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": null,
                                  "name": "管理方向-%s",
                                  "sortOrder": 1,
                                  "enabled": true
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("管理方向-" + suffix))
                .andReturn());
        createdDirectionIds.add(rootId);

        long childId = extractId(mockMvc.perform(post("/api/v1/admin/directions")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": %d,
                                  "name": "管理子方向-%s",
                                  "sortOrder": 2,
                                  "enabled": true
                                }
                                """.formatted(rootId, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(rootId))
                .andReturn());
        createdDirectionIds.add(childId);

        MvcResult listResult = mockMvc.perform(get("/api/v1/admin/directions")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(listResult.getResponse().getContentAsString()).contains("管理方向-" + suffix);

        mockMvc.perform(put("/api/v1/admin/directions/{directionId}", childId)
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": %d,
                                  "name": "管理子方向更新-%s",
                                  "sortOrder": 3,
                                  "enabled": true
                                }
                                """.formatted(rootId, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("管理子方向更新-" + suffix));

        mockMvc.perform(delete("/api/v1/admin/directions/{directionId}", childId)
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue()))
                .andExpect(status().isOk());
        createdDirectionIds.remove(childId);

        mockMvc.perform(delete("/api/v1/admin/directions/{directionId}", rootId)
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue()))
                .andExpect(status().isOk());
        createdDirectionIds.remove(rootId);
    }

    @Test
    void adminShouldManagePeriodsAndRejectOverlappingUpdate() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        User admin = createAdminUser(suffix);
        AuthCookies authCookies = loginAs(admin.getEmail(), "AdminPass123");

        OffsetDateTime registrationStart = OffsetDateTime.now().plusDays(1);
        OffsetDateTime registrationEnd = registrationStart.plusDays(2);
        OffsetDateTime selectionStart = registrationEnd.plusDays(1);
        OffsetDateTime selectionEnd = selectionStart.plusDays(2);
        OffsetDateTime interviewStart = selectionEnd.plusDays(1);
        OffsetDateTime interviewEnd = interviewStart.plusDays(2);

        mockMvc.perform(post("/api/v1/admin/periods")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periods": [
                                    {
                                      "periodType": "REGISTRATION",
                                      "startTime": "%s",
                                      "endTime": "%s",
                                      "enabled": true
                                    },
                                    {
                                      "periodType": "SELECTION",
                                      "startTime": "%s",
                                      "endTime": "%s",
                                      "enabled": true
                                    },
                                    {
                                      "periodType": "INTERVIEW",
                                      "startTime": "%s",
                                      "endTime": "%s",
                                      "enabled": false
                                    }
                                  ]
                                }
                                """.formatted(registrationStart, registrationEnd, selectionStart, selectionEnd, interviewStart, interviewEnd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].periodType").value("REGISTRATION"));

        Long selectionPeriodId = recruitmentPeriodRepository.findAll().stream()
                .filter(period -> period.getPeriodType() == PeriodType.SELECTION)
                .map(RecruitmentPeriod::getId)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(put("/api/v1/admin/periods/{periodId}", selectionPeriodId)
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodType": "SELECTION",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "enabled": true
                                }
                                """.formatted(registrationStart.plusHours(12), registrationEnd.plusHours(12))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("时期时间不能重叠"));
    }

    private User createAdminUser(String suffix) {
        String email = "admin_config_" + suffix + "@example.com";
        createdEmails.add(email);
        return userRepository.save(User.builder()
                .username("admin_config_" + suffix)
                .email(email)
                .passwordHash(passwordEncoder.encode("AdminPass123"))
                .emailVerified(true)
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .build());
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

    private long extractId(MvcResult result) throws IOException {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private RecruitmentPeriod copyPeriod(RecruitmentPeriod source) {
        return RecruitmentPeriod.builder()
                .id(source.getId())
                .periodType(source.getPeriodType())
                .startTime(source.getStartTime())
                .endTime(source.getEndTime())
                .enabled(source.getEnabled())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }

    private void restorePeriods() {
        List<RecruitmentPeriod> current = recruitmentPeriodRepository.findAll();
        for (RecruitmentPeriod period : current) {
            boolean existsInSnapshot = periodSnapshots.stream().anyMatch(snapshot -> snapshot.getId().equals(period.getId()));
            if (!existsInSnapshot) {
                recruitmentPeriodRepository.delete(period);
            }
        }

        for (RecruitmentPeriod snapshot : periodSnapshots.stream()
                .sorted(Comparator.comparing(RecruitmentPeriod::getId))
                .toList()) {
            recruitmentPeriodRepository.findById(snapshot.getId()).ifPresentOrElse(period -> {
                period.setPeriodType(snapshot.getPeriodType());
                period.setStartTime(snapshot.getStartTime());
                period.setEndTime(snapshot.getEndTime());
                period.setEnabled(snapshot.getEnabled());
                recruitmentPeriodRepository.save(period);
            }, () -> recruitmentPeriodRepository.save(copyPeriod(snapshot)));
        }
    }

    private record AuthCookies(Cookie authCookie, Cookie csrfCookie) {
    }
}
