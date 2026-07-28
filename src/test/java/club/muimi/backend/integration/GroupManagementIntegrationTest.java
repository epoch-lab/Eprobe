package club.muimi.backend.integration;

import club.muimi.backend.common.enums.*;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
class GroupManagementIntegrationTest {

    private final List<String> createdEmails = new ArrayList<>();
    private final List<Long> createdDirectionIds = new ArrayList<>();
    private final List<Long> createdApplicationIds = new ArrayList<>();
    private final List<Long> createdGroupIds = new ArrayList<>();
    private final List<Long> createdGroupMemberIds = new ArrayList<>();
    private final List<Long> createdTaskIds = new ArrayList<>();
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
    private RecruitmentTaskRepository recruitmentTaskRepository;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;
    private RecruitmentPeriod originalSelectionPeriodSnapshot;
    private Long managedSelectionPeriodId;
    private boolean createdSelectionPeriodForTest;

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
        for (Long taskId : createdTaskIds) {
            recruitmentTaskRepository.findById(taskId).ifPresent(recruitmentTaskRepository::delete);
        }
        createdTaskIds.clear();
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
        restoreSelectionPeriod();
    }

    @Test
    void adminShouldViewUngroupedApplicationsAndAssignToAnyMatchingGroup() throws Exception {
        openSelectionPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User admin = createUser("admin_group_" + suffix, "AdminPass123", Role.ADMIN);
        User freshman = createUser("freshman_group_" + suffix, "FreshPass123", Role.FRESHMAN);
        Direction root = createDirection(null, "后端-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "Java-" + suffix, 2, true);
        Application application = createApplication(freshman.getId(), root.getId(), child.getId(), Grade.YEAR_1, 2026);
        RecruitmentGroup group = createGroup("后端-Java-1组-" + suffix, root.getId(), child.getId(), Grade.YEAR_1, 2026, 10, null);

        AuthCookies adminCookies = loginAs(admin.getEmail(), "AdminPass123");

        mockMvc.perform(get("/api/v1/admin/groups/ungrouped-applications")
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(application.getId()))
                .andExpect(jsonPath("$.data[0].username").value(freshman.getUsername()));

        mockMvc.perform(post("/api/v1/admin/groups/{groupId}/applications/{applicationId}", group.getId(), application.getId())
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", adminCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        GroupMember groupMember = groupMemberRepository.findByApplicationId(application.getId()).orElseThrow();
        createdGroupMemberIds.add(groupMember.getId());
        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.GROUPED);
    }

    @Test
    void adminShouldCreateMarkdownOnlyTaskWithoutAttachment() throws Exception {
        openSelectionPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User admin = createUser("admin_task_" + suffix, "AdminPass123", Role.ADMIN);
        Direction root = createDirection(null, "任务方向-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "任务子方向-" + suffix, 2, true);
        RecruitmentGroup group = createGroup("任务组-" + suffix, root.getId(), child.getId(), Grade.YEAR_1, 2026, 10, null);
        AuthCookies adminCookies = loginAs(admin.getEmail(), "AdminPass123");

        long taskId = extractId(mockMvc.perform(post("/api/v1/admin/groups/{groupId}/tasks", group.getId())
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", adminCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Markdown任务-%s",
                                  "contentMarkdown": "# 任务说明\\n\\n只提交 markdown。",
                                  "removeAttachment": false,
                                  "maxScore": 100,
                                  "deadlineAt": "%s"
                                }
                                """.formatted(suffix, OffsetDateTime.now().plusDays(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Markdown任务-" + suffix))
                .andExpect(jsonPath("$.data.contentMarkdown").value("# 任务说明\n\n只提交 markdown。"))
                .andReturn());
        createdTaskIds.add(taskId);

        RecruitmentTask task = recruitmentTaskRepository.findById(taskId).orElseThrow();
        assertThat(task.getAttachmentFileId()).isNull();
        assertThat(task.getContentMarkdown()).isEqualTo("# 任务说明\n\n只提交 markdown。");
    }

    @Test
    void leaderShouldSeeUngroupedApplicationsButOnlyAssignToOwnedGroup() throws Exception {
        openSelectionPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User leader = createUser("leader_group_" + suffix, "LeaderPass123", Role.LEADER);
        User admin = createUser("admin_other_" + suffix, "AdminPass123", Role.ADMIN);
        User freshman = createUser("freshman_other_" + suffix, "FreshPass123", Role.FRESHMAN);
        Direction root = createDirection(null, "前端-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "Vue-" + suffix, 2, true);
        Application application = createApplication(freshman.getId(), root.getId(), child.getId(), Grade.YEAR_2, 2025);
        RecruitmentGroup ownedGroup = createGroup("前端-Vue-1组-" + suffix, root.getId(), child.getId(), Grade.YEAR_2, 2025, 10, leader.getId());
        RecruitmentGroup otherGroup = createGroup("前端-Vue-2组-" + suffix, root.getId(), child.getId(), Grade.YEAR_2, 2025, 10, admin.getId());

        AuthCookies leaderCookies = loginAs(leader.getEmail(), "LeaderPass123");

        mockMvc.perform(get("/api/v1/leader/groups/ungrouped-applications")
                        .cookie(leaderCookies.authCookie(), leaderCookies.csrfCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(application.getId()));

        mockMvc.perform(post("/api/v1/leader/groups/{groupId}/applications/{applicationId}", otherGroup.getId(), application.getId())
                        .cookie(leaderCookies.authCookie(), leaderCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", leaderCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/leader/groups/{groupId}/applications/{applicationId}", ownedGroup.getId(), application.getId())
                        .cookie(leaderCookies.authCookie(), leaderCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", leaderCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        GroupMember groupMember = groupMemberRepository.findByApplicationId(application.getId()).orElseThrow();
        createdGroupMemberIds.add(groupMember.getId());
        assertThat(groupMember.getGroupId()).isEqualTo(ownedGroup.getId());
    }

    @Test
    void concurrentAssignmentsShouldNotOverfillGroupCapacity() throws Exception {
        openSelectionPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User admin = createUser("admin_concurrent_" + suffix, "AdminPass123", Role.ADMIN);
        User freshmanOne = createUser("freshman_one_" + suffix, "FreshPass123", Role.FRESHMAN);
        User freshmanTwo = createUser("freshman_two_" + suffix, "FreshPass123", Role.FRESHMAN);
        Direction root = createDirection(null, "算法-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "竞赛-" + suffix, 2, true);
        Application applicationOne = createApplication(freshmanOne.getId(), root.getId(), child.getId(), Grade.YEAR_1, 2026);
        Application applicationTwo = createApplication(freshmanTwo.getId(), root.getId(), child.getId(), Grade.YEAR_1, 2026);
        RecruitmentGroup group = createGroup("算法-竞赛-1组-" + suffix, root.getId(), child.getId(), Grade.YEAR_1, 2026, 1, null);
        AuthCookies adminCookies = loginAs(admin.getEmail(), "AdminPass123");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Integer> firstAttempt = executor.submit(() ->
                    assignApplicationConcurrently(group.getId(), applicationOne.getId(), adminCookies, readyLatch, startLatch));
            Future<Integer> secondAttempt = executor.submit(() ->
                    assignApplicationConcurrently(group.getId(), applicationTwo.getId(), adminCookies, readyLatch, startLatch));

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<Integer> statuses = List.of(firstAttempt.get(10, TimeUnit.SECONDS), secondAttempt.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        groupMemberRepository.findByApplicationId(applicationOne.getId())
                .ifPresent(groupMember -> createdGroupMemberIds.add(groupMember.getId()));
        groupMemberRepository.findByApplicationId(applicationTwo.getId())
                .ifPresent(groupMember -> createdGroupMemberIds.add(groupMember.getId()));
        assertThat(groupMemberRepository.countByGroupId(group.getId())).isEqualTo(1);
        assertThat(applicationRepository.findById(applicationOne.getId()).orElseThrow().getStatus())
                .isIn(ApplicationStatus.SUBMITTED, ApplicationStatus.GROUPED);
        assertThat(applicationRepository.findById(applicationTwo.getId()).orElseThrow().getStatus())
                .isIn(ApplicationStatus.SUBMITTED, ApplicationStatus.GROUPED);
    }

    @Test
    void concurrentAssignmentsOfSameApplicationShouldOnlyJoinOneGroup() throws Exception {
        openSelectionPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User admin = createUser("admin_same_app_" + suffix, "AdminPass123", Role.ADMIN);
        User freshman = createUser("freshman_same_app_" + suffix, "FreshPass123", Role.FRESHMAN);
        Direction root = createDirection(null, "测试方向-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "测试子方向-" + suffix, 2, true);
        Application application = createApplication(freshman.getId(), root.getId(), child.getId(), Grade.YEAR_1, 2026);
        RecruitmentGroup firstGroup = createGroup("测试组1-" + suffix, root.getId(), child.getId(), Grade.YEAR_1, 2026, 5, null);
        RecruitmentGroup secondGroup = createGroup("测试组2-" + suffix, root.getId(), child.getId(), Grade.YEAR_1, 2026, 5, null);
        AuthCookies adminCookies = loginAs(admin.getEmail(), "AdminPass123");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Integer> firstAttempt = executor.submit(() ->
                    assignApplicationConcurrently(firstGroup.getId(), application.getId(), adminCookies, readyLatch, startLatch));
            Future<Integer> secondAttempt = executor.submit(() ->
                    assignApplicationConcurrently(secondGroup.getId(), application.getId(), adminCookies, readyLatch, startLatch));

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<Integer> statuses = List.of(firstAttempt.get(10, TimeUnit.SECONDS), secondAttempt.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        GroupMember groupMember = groupMemberRepository.findByApplicationId(application.getId()).orElseThrow();
        createdGroupMemberIds.add(groupMember.getId());
        assertThat(groupMember.getGroupId()).isIn(firstGroup.getId(), secondGroup.getId());
        assertThat(groupMemberRepository.countByGroupId(firstGroup.getId()) + groupMemberRepository.countByGroupId(secondGroup.getId()))
                .isEqualTo(1);
    }

    @Test
    void adminShouldManageGroupCrudAndLeaderAssignment() throws Exception {
        openSelectionPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User admin = createUser("admin_crud_" + suffix, "AdminPass123", Role.ADMIN);
        User leader = createUser("leader_crud_" + suffix, "LeaderPass123", Role.LEADER);
        Direction root = createDirection(null, "管理方向-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "管理子方向-" + suffix, 2, true);
        AuthCookies adminCookies = loginAs(admin.getEmail(), "AdminPass123");

        long groupId = extractGroupId(mockMvc.perform(post("/api/v1/admin/groups")
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", adminCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "管理组-%s",
                                  "directionLevel1Id": %d,
                                  "directionLevel2Id": %d,
                                  "grade": "YEAR_1",
                                  "admissionYear": 2026,
                                  "maxSize": 10
                                }
                                """.formatted(suffix, root.getId(), child.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("管理组-" + suffix))
                .andReturn());
        createdGroupIds.add(groupId);

        mockMvc.perform(put("/api/v1/admin/groups/{groupId}/leader", groupId)
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", adminCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leaderUserId": %d
                                }
                                """.formatted(leader.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaderUserId").value(leader.getId()));

        mockMvc.perform(put("/api/v1/admin/groups/{groupId}", groupId)
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", adminCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "管理组更新-%s",
                                  "directionLevel1Id": %d,
                                  "directionLevel2Id": %d,
                                  "grade": "YEAR_1",
                                  "admissionYear": 2026,
                                  "maxSize": 12
                                }
                                """.formatted(suffix, root.getId(), child.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("管理组更新-" + suffix))
                .andExpect(jsonPath("$.data.maxSize").value(12));

        mockMvc.perform(delete("/api/v1/admin/groups/{groupId}/leader", groupId)
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", adminCookies.csrfCookie().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaderUserId").doesNotExist());

        mockMvc.perform(delete("/api/v1/admin/groups/{groupId}", groupId)
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", adminCookies.csrfCookie().getValue()))
                .andExpect(status().isOk());

        createdGroupIds.remove(groupId);
        assertThat(recruitmentGroupRepository.findById(groupId)).isEmpty();
    }

    @Test
    void leaderShouldRejectMatchingApplicationAndUnassignOwnedGroupMember() throws Exception {
        openSelectionPeriod();
        String suffix = String.valueOf(System.nanoTime());
        User leader = createUser("leader_review_" + suffix, "LeaderPass123", Role.LEADER);
        User freshman = createUser("freshman_review_" + suffix, "FreshPass123", Role.FRESHMAN);
        User freshmanGrouped = createUser("freshman_grouped_" + suffix, "FreshPass123", Role.FRESHMAN);
        Direction root = createDirection(null, "审核方向-" + suffix, 1, true);
        Direction child = createDirection(root.getId(), "审核子方向-" + suffix, 2, true);
        Application rejectedApplication = createApplication(freshman.getId(), root.getId(), child.getId(), Grade.YEAR_1, 2026);
        Application groupedApplication = createApplication(freshmanGrouped.getId(), root.getId(), child.getId(), Grade.YEAR_1, 2026 + 1);
        RecruitmentGroup reviewGroup = createGroup("审核组-" + suffix, root.getId(), child.getId(), Grade.YEAR_1, 2026, 10, leader.getId());
        RecruitmentGroup managedGroup = createGroup("取消分组组-" + suffix, root.getId(), child.getId(), Grade.YEAR_1, 2027, 10, leader.getId());
        GroupMember groupMember = groupMemberRepository.save(GroupMember.builder()
                .groupId(managedGroup.getId())
                .userId(freshmanGrouped.getId())
                .applicationId(groupedApplication.getId())
                .build());
        createdGroupMemberIds.add(groupMember.getId());
        groupedApplication.setStatus(ApplicationStatus.GROUPED);
        applicationRepository.save(groupedApplication);
        AuthCookies leaderCookies = loginAs(leader.getEmail(), "LeaderPass123");

        mockMvc.perform(post("/api/v1/leader/applications/{applicationId}/reject", rejectedApplication.getId())
                        .cookie(leaderCookies.authCookie(), leaderCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", leaderCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "当前条件不符合"
                                }
                                """))
                .andExpect(status().isOk());

        Application rejected = applicationRepository.findById(rejectedApplication.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(rejected.getStatusRemark()).isEqualTo("当前条件不符合");

        mockMvc.perform(post("/api/v1/leader/groups/{groupId}/applications/{applicationId}/unassign", managedGroup.getId(), groupedApplication.getId())
                        .cookie(leaderCookies.authCookie(), leaderCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", leaderCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "调整分组"
                                }
                                """))
                .andExpect(status().isOk());

        createdGroupMemberIds.remove(groupMember.getId());
        assertThat(groupMemberRepository.findByApplicationId(groupedApplication.getId())).isEmpty();
        Application unassigned = applicationRepository.findById(groupedApplication.getId()).orElseThrow();
        assertThat(unassigned.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(unassigned.getStatusRemark()).isEqualTo("调整分组");
        assertThat(reviewGroup.getLeaderUserId()).isEqualTo(leader.getId());
    }

    private User createUser(String baseName, String password, Role role) {
        String email = baseName + "@example.com";
        createdEmails.add(email);
        return userRepository.save(User.builder()
                .username(baseName)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .emailVerified(true)
                .role(role)
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

    private Application createApplication(Long userId, Long level1Id, Long level2Id, Grade grade, int admissionYear) {
        Application application = applicationRepository.save(Application.builder()
                .userId(userId)
                .realName("张三")
                .phoneNumber("13800000000")
                .college("计算机学院")
                .major("软件工程")
                .className("1班")
                .grade(grade)
                .admissionYear(admissionYear)
                .directionLevel1Id(level1Id)
                .directionLevel2Id(level2Id)
                .introduction("待分组")
                .status(ApplicationStatus.SUBMITTED)
                .build());
        createdApplicationIds.add(application.getId());
        return application;
    }

    private RecruitmentGroup createGroup(
            String name,
            Long level1Id,
            Long level2Id,
            Grade grade,
            int admissionYear,
            int maxSize,
            Long leaderUserId
    ) {
        RecruitmentGroup group = recruitmentGroupRepository.save(RecruitmentGroup.builder()
                .name(name)
                .directionLevel1Id(level1Id)
                .directionLevel2Id(level2Id)
                .grade(grade)
                .admissionYear(admissionYear)
                .maxSize(maxSize)
                .leaderUserId(leaderUserId)
                .build());
        createdGroupIds.add(group.getId());
        return group;
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

    private long extractGroupId(org.springframework.test.web.servlet.MvcResult result) throws IOException {
        return extractId(result);
    }

    private long extractId(org.springframework.test.web.servlet.MvcResult result) throws IOException {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private int assignApplicationConcurrently(
            Long groupId,
            Long applicationId,
            AuthCookies adminCookies,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) throws Exception {
        readyLatch.countDown();
        if (!startLatch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发测试启动超时");
        }
        return mockMvc.perform(post("/api/v1/admin/groups/{groupId}/applications/{applicationId}", groupId, applicationId)
                        .cookie(adminCookies.authCookie(), adminCookies.csrfCookie())
                        .header("X-CSRF-TOKEN", adminCookies.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void openSelectionPeriod() {
        RecruitmentPeriod period = recruitmentPeriodRepository.findAll()
                .stream()
                .filter(item -> item.getPeriodType() == PeriodType.SELECTION)
                .findFirst()
                .orElse(null);

        if (period == null) {
            RecruitmentPeriod created = recruitmentPeriodRepository.save(RecruitmentPeriod.builder()
                    .periodType(PeriodType.SELECTION)
                    .startTime(LocalDateTime.now().minusDays(1))
                    .endTime(LocalDateTime.now().plusDays(1))
                    .enabled(true)
                    .build());
            managedSelectionPeriodId = created.getId();
            createdSelectionPeriodForTest = true;
            return;
        }

        managedSelectionPeriodId = period.getId();
        createdSelectionPeriodForTest = false;
        originalSelectionPeriodSnapshot = RecruitmentPeriod.builder()
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

    private void restoreSelectionPeriod() {
        if (managedSelectionPeriodId == null) {
            return;
        }

        if (createdSelectionPeriodForTest) {
            recruitmentPeriodRepository.deleteById(managedSelectionPeriodId);
        } else if (originalSelectionPeriodSnapshot != null) {
            recruitmentPeriodRepository.findById(managedSelectionPeriodId).ifPresent(period -> {
                period.setEnabled(originalSelectionPeriodSnapshot.getEnabled());
                period.setStartTime(originalSelectionPeriodSnapshot.getStartTime());
                period.setEndTime(originalSelectionPeriodSnapshot.getEndTime());
                recruitmentPeriodRepository.save(period);
            });
        }

        managedSelectionPeriodId = null;
        createdSelectionPeriodForTest = false;
        originalSelectionPeriodSnapshot = null;
    }

    private record AuthCookies(Cookie authCookie, Cookie csrfCookie) {
    }
}
