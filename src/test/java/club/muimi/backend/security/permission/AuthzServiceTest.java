package club.muimi.backend.security.permission;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.entity.Application;
import club.muimi.backend.entity.GroupMember;
import club.muimi.backend.repository.ApplicationRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.security.auth.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthzServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private RecruitmentGroupRepository recruitmentGroupRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isCurrentUserShouldReturnTrueForAuthenticatedOwner() {
        AuthzService authzService = new AuthzService(applicationRepository, groupMemberRepository, recruitmentGroupRepository);
        LoginUser loginUser = new LoginUser(9L, "owner", "owner@example.com", "hashed", Role.FRESHMAN, UserStatus.ACTIVE, 0L, "jti");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(loginUser, null, loginUser.getAuthorities())
        );

        assertThat(authzService.isCurrentUser(9L)).isTrue();
        assertThat(authzService.isCurrentUser(10L)).isFalse();
    }

    @Test
    void adminShouldBeAbleToViewAnyGroup() {
        AuthzService authzService = new AuthzService(applicationRepository, groupMemberRepository, recruitmentGroupRepository);
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");

        boolean result = authzService.canViewGroup(
                UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()),
                100L
        );

        assertThat(result).isTrue();
    }

    @Test
    void leaderShouldOnlyManageOwnGroup() {
        AuthzService authzService = new AuthzService(applicationRepository, groupMemberRepository, recruitmentGroupRepository);
        LoginUser leader = new LoginUser(3L, "leader", "leader@example.com", "hashed", Role.LEADER, UserStatus.ACTIVE, 0L, "jti-leader");
        when(recruitmentGroupRepository.existsByIdAndLeaderUserId(10L, 3L)).thenReturn(true);
        when(recruitmentGroupRepository.existsByIdAndLeaderUserId(11L, 3L)).thenReturn(false);

        assertThat(authzService.canManageGroup(
                UsernamePasswordAuthenticationToken.authenticated(leader, null, leader.getAuthorities()),
                10L
        )).isTrue();
        assertThat(authzService.canManageGroup(
                UsernamePasswordAuthenticationToken.authenticated(leader, null, leader.getAuthorities()),
                11L
        )).isFalse();
    }

    @Test
    void freshmanShouldViewOwnGroupButNotManageIt() {
        AuthzService authzService = new AuthzService(applicationRepository, groupMemberRepository, recruitmentGroupRepository);
        LoginUser freshman = new LoginUser(7L, "freshman", "freshman@example.com", "hashed", Role.FRESHMAN, UserStatus.ACTIVE, 0L, "jti-freshman");
        when(recruitmentGroupRepository.existsByIdAndLeaderUserId(20L, 7L)).thenReturn(false);
        when(groupMemberRepository.existsByUserIdAndGroupId(7L, 20L)).thenReturn(true);

        assertThat(authzService.canViewGroup(
                UsernamePasswordAuthenticationToken.authenticated(freshman, null, freshman.getAuthorities()),
                20L
        )).isTrue();
        assertThat(authzService.canManageGroup(
                UsernamePasswordAuthenticationToken.authenticated(freshman, null, freshman.getAuthorities()),
                20L
        )).isFalse();
    }

    @Test
    void leaderShouldAccessApplicationWhenItBelongsToManagedGroup() {
        AuthzService authzService = new AuthzService(applicationRepository, groupMemberRepository, recruitmentGroupRepository);
        LoginUser leader = new LoginUser(5L, "leader", "leader@example.com", "hashed", Role.LEADER, UserStatus.ACTIVE, 0L, "jti-leader");
        when(applicationRepository.findById(30L)).thenReturn(Optional.of(Application.builder()
                .id(30L)
                .userId(99L)
                .realName("张三")
                .phoneNumber("13800000000")
                .college("计算机学院")
                .major("软件工程")
                .className("1班")
                .grade(club.muimi.backend.common.enums.Grade.YEAR_1)
                .admissionYear(2026)
                .directionLevel1Id(1L)
                .directionLevel2Id(2L)
                .build()));
        when(groupMemberRepository.findByApplicationId(30L)).thenReturn(Optional.of(GroupMember.builder()
                .id(1L)
                .groupId(200L)
                .userId(99L)
                .applicationId(30L)
                .build()));
        when(recruitmentGroupRepository.existsByIdAndLeaderUserId(200L, 5L)).thenReturn(true);

        boolean result = authzService.canAccessApplication(
                UsernamePasswordAuthenticationToken.authenticated(leader, null, leader.getAuthorities()),
                30L
        );

        assertThat(result).isTrue();
    }
}
