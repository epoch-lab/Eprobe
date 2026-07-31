package club.muimi.backend.service.admin;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.admin.UpdateUserRoleRequest;
import club.muimi.backend.dto.admin.UpdateUserStatusRequest;
import club.muimi.backend.dto.admin.CreateUserRequest;
import club.muimi.backend.dto.admin.UpdateUserRequest;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.ApplicationRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.user.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private RecruitmentGroupRepository recruitmentGroupRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                applicationRepository,
                groupMemberRepository,
                recruitmentGroupRepository,
                currentUserService,
                auditLogService,
                passwordEncoder
        );
    }

    @Test
    void updateUserStatusShouldRejectSelfStatusChange() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        when(currentUserService.requireCurrentUser()).thenReturn(admin);

        assertThatThrownBy(() -> adminUserService.updateUserStatus(1L, new UpdateUserStatusRequest(UserStatus.DISABLED)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("管理员不能修改自己的状态");
    }

    @Test
    void updateUserRoleShouldPromoteFreshmanToLeaderAndInvalidateOldTokens() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        User freshman = User.builder()
                .id(2L)
                .username("freshman")
                .email("freshman@example.com")
                .passwordHash("hashed")
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .tokenVersion(3L)
                .build();
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(freshman));
        when(groupMemberRepository.findAllByUserId(2L)).thenReturn(List.of());
        when(recruitmentGroupRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());
        when(applicationRepository.countByUserId(2L)).thenReturn(0L);

        var result = adminUserService.updateUserRole(2L, new UpdateUserRoleRequest(Role.LEADER));

        assertThat(result.role()).isEqualTo(Role.LEADER);
        assertThat(freshman.getRole()).isEqualTo(Role.LEADER);
        assertThat(freshman.getTokenVersion()).isEqualTo(4L);
    }

    @Test
    void updateUserRoleShouldRejectLeaderDemotionWhenUserStillOwnsGroup() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        User leader = User.builder()
                .id(2L)
                .username("leader")
                .email("leader@example.com")
                .passwordHash("hashed")
                .role(Role.LEADER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .tokenVersion(3L)
                .build();
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(leader));
        when(recruitmentGroupRepository.findAllByLeaderUserId(2L)).thenReturn(List.of(
                RecruitmentGroup.builder().id(10L).leaderUserId(2L).name("g1").directionLevel1Id(1L).directionLevel2Id(2L).grade(club.muimi.backend.common.enums.Grade.YEAR_1).admissionYear(2026).maxSize(10).build()
        ));

        assertThatThrownBy(() -> adminUserService.updateUserRole(2L, new UpdateUserRoleRequest(Role.FRESHMAN)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("该负责人仍绑定负责的分组，不能降级为新生");
        assertThat(leader.getRole()).isEqualTo(Role.LEADER);
        assertThat(leader.getTokenVersion()).isEqualTo(3L);
    }

    @Test
    void updateUserRoleShouldRejectAdminRoleRequest() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        when(currentUserService.requireCurrentUser()).thenReturn(admin);

        assertThatThrownBy(() -> adminUserService.updateUserRole(2L, new UpdateUserRoleRequest(Role.ADMIN)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("用户角色只能在 FRESHMAN 与 LEADER 之间调整");
    }

    @Test
    void updateUserRoleShouldRejectChangingAdminAccount() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        User targetAdmin = User.builder()
                .id(2L)
                .username("admin2")
                .email("admin2@example.com")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .tokenVersion(3L)
                .build();
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        assertThatThrownBy(() -> adminUserService.updateUserRole(2L, new UpdateUserRoleRequest(Role.LEADER)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("管理员账号角色不允许通过该接口修改");
        assertThat(targetAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(targetAdmin.getTokenVersion()).isEqualTo(3L);
    }

    @Test
    void updateUserRoleShouldDemoteLeaderWithoutOwnedGroup() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        User leader = User.builder()
                .id(2L)
                .username("leader")
                .email("leader@example.com")
                .passwordHash("hashed")
                .role(Role.LEADER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .tokenVersion(3L)
                .build();
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(leader));
        when(recruitmentGroupRepository.findAllByLeaderUserId(2L)).thenReturn(List.of());
        when(groupMemberRepository.findAllByUserId(2L)).thenReturn(List.of());
        when(recruitmentGroupRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());
        when(applicationRepository.countByUserId(2L)).thenReturn(0L);

        var result = adminUserService.updateUserRole(2L, new UpdateUserRoleRequest(Role.FRESHMAN));

        assertThat(result.role()).isEqualTo(Role.FRESHMAN);
        assertThat(leader.getRole()).isEqualTo(Role.FRESHMAN);
        assertThat(leader.getTokenVersion()).isEqualTo(4L);
    }

    @Test
    void listUsersShouldTolerateLeaderOwningMultipleGroups() {
        User leader = User.builder()
                .id(2L)
                .username("leader")
                .email("leader@example.com")
                .passwordHash("hashed")
                .role(Role.LEADER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        when(userRepository.searchUsers(null, null, null, PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
                .thenReturn(new PageImpl<>(List.of(leader)));
        when(applicationRepository.findAllByUserIdIn(List.of(2L))).thenReturn(List.of());
        when(groupMemberRepository.findAllByUserIdIn(List.of(2L))).thenReturn(List.of());
        when(recruitmentGroupRepository.findAllByLeaderUserIdIn(List.of(2L))).thenReturn(List.of(
                RecruitmentGroup.builder().id(20L).leaderUserId(2L).name("g2").directionLevel1Id(1L).directionLevel2Id(2L).grade(club.muimi.backend.common.enums.Grade.YEAR_1).admissionYear(2026).maxSize(10).build(),
                RecruitmentGroup.builder().id(10L).leaderUserId(2L).name("g1").directionLevel1Id(1L).directionLevel2Id(2L).grade(club.muimi.backend.common.enums.Grade.YEAR_1).admissionYear(2026).maxSize(10).build()
        ));

        var result = adminUserService.listUsers(1, 10, null, null, null);

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().getFirst().leaderGroupCount()).isEqualTo(2L);
    }

    @Test
    void getUserDetailShouldTolerateLeaderOwningMultipleGroups() {
        User leader = User.builder()
                .id(2L)
                .username("leader")
                .email("leader@example.com")
                .passwordHash("hashed")
                .role(Role.LEADER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(leader));
        when(groupMemberRepository.findAllByUserId(2L)).thenReturn(List.of());
        when(recruitmentGroupRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(
                RecruitmentGroup.builder().id(20L).leaderUserId(2L).name("g2").directionLevel1Id(1L).directionLevel2Id(2L).grade(club.muimi.backend.common.enums.Grade.YEAR_1).admissionYear(2026).maxSize(10).build(),
                RecruitmentGroup.builder().id(10L).leaderUserId(2L).name("g1").directionLevel1Id(1L).directionLevel2Id(2L).grade(club.muimi.backend.common.enums.Grade.YEAR_1).admissionYear(2026).maxSize(10).build()
        ));
        when(applicationRepository.countByUserId(2L)).thenReturn(0L);

        var result = adminUserService.getUserDetail(2L);

        assertThat(result.leaderGroups())
                .extracting(club.muimi.backend.vo.auth.GroupSimpleVo::id)
                .containsExactly(20L, 10L);
    }

    @Test
    void createUserShouldRequireHighestAdminAndHashPassword() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(userRepository.findByEmail("new_user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded");
        User created = User.builder().id(8L).username("new_user").email("new_user@example.com")
                .passwordHash("encoded").role(Role.LEADER).status(UserStatus.ACTIVE).emailVerified(true).tokenVersion(0L).build();
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(created);
        when(groupMemberRepository.findAllByUserId(8L)).thenReturn(List.of());
        when(recruitmentGroupRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(8L)).thenReturn(List.of());
        when(applicationRepository.countByUserId(8L)).thenReturn(0L);

        var result = adminUserService.createUser(new CreateUserRequest("new_user", "new_user@example.com", "NewPass123", "NewPass123", Role.LEADER, UserStatus.ACTIVE, true));

        assertThat(result.id()).isEqualTo(8L);
        verify(passwordEncoder).encode("NewPass123");
    }

    @Test
    void createUserShouldRejectNonAdmin() {
        when(currentUserService.requireCurrentUser()).thenReturn(new LoginUser(2L, "leader", "leader@example.com", "hashed", Role.LEADER, UserStatus.ACTIVE, 0L, "jti"));
        assertThatThrownBy(() -> adminUserService.createUser(new CreateUserRequest("new_user", "new@example.com", "NewPass123", "NewPass123", Role.FRESHMAN, UserStatus.ACTIVE, true)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("仅最高管理员可以执行此操作");
    }

    @Test
    void deleteUserShouldRejectReferencedUser() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        User target = User.builder().id(2L).username("target").email("target@example.com").passwordHash("hash").role(Role.FRESHMAN).status(UserStatus.ACTIVE).build();
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("fk"))
                .when(userRepository).flush();

        assertThatThrownBy(() -> adminUserService.deleteUser(2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("该用户仍有关联业务数据，无法删除");
    }

    @Test
    void updateUserShouldNotAllowAdminToChangeOwnRoleOrStatus() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        when(currentUserService.requireCurrentUser()).thenReturn(admin);

        assertThatThrownBy(() -> adminUserService.updateUser(1L,
                new UpdateUserRequest("admin", "admin@example.com", null, null, Role.FRESHMAN, UserStatus.ACTIVE, true)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("管理员不能修改自己的角色或状态");
    }
}
