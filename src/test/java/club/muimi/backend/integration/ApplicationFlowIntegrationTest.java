package club.muimi.backend.integration;

import club.muimi.backend.common.enums.Grade;
import club.muimi.backend.common.enums.PeriodType;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.auth.LoginRequest;
import club.muimi.backend.entity.*;
import club.muimi.backend.repository.*;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        "app.recruitment.application.allowed-grades=YEAR_1,YEAR_2",
        "app.bootstrap.default-admin.enabled=false"
})
class ApplicationFlowIntegrationTest {

    private final List<String> createdEmails = new ArrayList<>();
    private final List<Long> createdDirectionIds = new ArrayList<>();
    private final List<Long> createdApplicationIds = new ArrayList<>();
    private final List<Long> createdGroupIds = new ArrayList<>();
    private final List<Long> createdGroupMemberIds = new ArrayList<>();
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DirectionRepository directionRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private RecruitmentGroupRepository recruitmentGroupRepository;
    @Autowired
    private RecruitmentPeriodRepository recruitmentPeriodRepository;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;
    private RecruitmentPeriod originalRegistrationPeriodSnapshot;
    private Long managedRegistrationPeriodId;
    private boolean createdRegistrationPeriodForTest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanUp() {
        for (Long groupMemberId : createdGroupMemberIds) {
            groupMemberRepository.findById(groupMemberId).ifPresent(groupMemberRepository::delete);
        }
        createdGroupMemberIds.clear();
        for (Long applicationId : createdApplicationIds) {
            applicationRepository.findById(applicationId).ifPresent(applicationRepository::delete);
        }
        createdApplicationIds.clear();
        for (Long groupId : createdGroupIds) {
            recruitmentGroupRepository.findById(groupId).ifPresent(recruitmentGroupRepository::delete);
        }
        createdGroupIds.clear();
        for (int i = createdDirectionIds.size() - 1; i >= 0; i--) {
            Long directionId = createdDirectionIds.get(i);
            directionRepository.findById(directionId).ifPresent(directionRepository::delete);
        }
        createdDirectionIds.clear();
        for (String email : createdEmails) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
        createdEmails.clear();
        restoreRegistrationPeriod();
    }

    @Test
    void freshmanShouldCreateWithdrawAndSummarizeApplications() throws Exception {
        openRegistrationPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User user = createFreshmanUser(suffix);
        Direction root = createDirection(null, "后端-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "Java-" + suffix, 2, true);
        AuthCookies authCookies = loginAs(user.getEmail(), "Pass1234");

        MvcResult createResult = mockMvc.perform(post("/api/v1/applications")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "realName": "张三",
                                  "phone": "13800000000",
                                  "college": "计算机学院",
                                  "major": "软件工程",
                                  "className": "软工1班",
                                  "grade": "YEAR_1",
                                  "admissionYear": 2026,
                                  "directionLevel1Id": %d,
                                  "directionLevel2Id": %d,
                                  "introduction": "喜欢后端开发"
                                }
                                """.formatted(root.getId(), child.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.directionLevel1Name").value(root.getName()))
                .andExpect(jsonPath("$.data.directionLevel2Name").value(child.getName()))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();

        long applicationId = extractApplicationId(createResult);
        createdApplicationIds.add(applicationId);

        mockMvc.perform(get("/api/v1/applications")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(applicationId))
                .andExpect(jsonPath("$.data[0].directionLevel2Name").value(child.getName()));

        mockMvc.perform(get("/api/v1/applications/summary")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationCount").value(1))
                .andExpect(jsonPath("$.data.submittedCount").value(1))
                .andExpect(jsonPath("$.data.groupedCount").value(0));

        mockMvc.perform(delete("/api/v1/applications/{applicationId}", applicationId)
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/applications/{applicationId}", applicationId)
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "realName": "张三",
                                  "phone": "13800000000",
                                  "college": "计算机学院",
                                  "major": "软件工程",
                                  "className": "软工1班",
                                  "grade": "YEAR_1",
                                  "admissionYear": 2026,
                                  "directionLevel1Id": %d,
                                  "directionLevel2Id": %d,
                                  "introduction": "更新介绍"
                                }
                                """.formatted(root.getId(), child.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("当前申请状态不允许修改"));
    }

    @Test
    void freshmanShouldBeRejectedWhenGradeIsNotAllowedOrApplicationAlreadyGrouped() throws Exception {
        openRegistrationPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User user = createFreshmanUser(suffix);
        Direction root = createDirection(null, "前端-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "Vue-" + suffix, 2, true);
        AuthCookies authCookies = loginAs(user.getEmail(), "Pass1234");

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "realName": "李四",
                                  "phone": "13800000001",
                                  "college": "计算机学院",
                                  "major": "软件工程",
                                  "className": "软工2班",
                                  "grade": "YEAR_3",
                                  "admissionYear": 2024,
                                  "directionLevel1Id": %d,
                                  "directionLevel2Id": %d
                                }
                                """.formatted(root.getId(), child.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("当前年级暂不允许报名"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/applications")
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "realName": "李四",
                                  "phone": "13800000001",
                                  "college": "计算机学院",
                                  "major": "软件工程",
                                  "className": "软工2班",
                                  "grade": "YEAR_2",
                                  "admissionYear": 2025,
                                  "directionLevel1Id": %d,
                                  "directionLevel2Id": %d
                                }
                                """.formatted(root.getId(), child.getId())))
                .andExpect(status().isOk())
                .andReturn();

        long applicationId = extractApplicationId(createResult);
        createdApplicationIds.add(applicationId);
        RecruitmentGroup group = recruitmentGroupRepository.save(RecruitmentGroup.builder()
                .name("前端-Vue-1组-" + suffix)
                .directionLevel1Id(root.getId())
                .directionLevel2Id(child.getId())
                .grade(Grade.YEAR_2)
                .admissionYear(2025)
                .maxSize(20)
                .build());
        createdGroupIds.add(group.getId());
        GroupMember groupMember = groupMemberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .userId(user.getId())
                .applicationId(applicationId)
                .build());
        createdGroupMemberIds.add(groupMember.getId());

        mockMvc.perform(put("/api/v1/applications/{applicationId}", applicationId)
                        .cookie(authCookies.authCookie(), authCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", authCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "realName": "李四",
                                  "phone": "13800000001",
                                  "college": "计算机学院",
                                  "major": "软件工程",
                                  "className": "软工2班",
                                  "grade": "YEAR_2",
                                  "admissionYear": 2025,
                                  "directionLevel1Id": %d,
                                  "directionLevel2Id": %d
                                }
                                """.formatted(root.getId(), child.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("已分组的申请不允许修改"));
    }

    private User createFreshmanUser(String suffix) {
        String email = "application_" + suffix + "@example.com";
        createdEmails.add(email);
        return userRepository.save(User.builder()
                .username("application_" + suffix)
                .email(email)
                .passwordHash(passwordEncoder.encode("Pass1234"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .lastLoginAt(LocalDateTime.now())
                .build());
    }

    private Direction createDirection(Long parentId, String name, int level, boolean enabled) {
        Direction direction = directionRepository.save(Direction.builder()
                .parentId(parentId)
                .name(name)
                .level(level)
                .sortOrder(1)
                .enabled(enabled)
                .build());
        createdDirectionIds.add(direction.getId());
        return direction;
    }

    private long extractApplicationId(MvcResult result) throws IOException {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
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

    private record AuthCookies(Cookie authCookie, Cookie csrfCookie) {
    }
}
