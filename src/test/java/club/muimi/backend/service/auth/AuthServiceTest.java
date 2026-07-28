package club.muimi.backend.service.auth;

import club.muimi.backend.common.enums.EmailCodeScene;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.config.AuthProperties;
import club.muimi.backend.dto.auth.*;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.TooManyRequestsException;
import club.muimi.backend.exception.UnauthorizedException;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.security.cookie.AuthCookieService;
import club.muimi.backend.security.jwt.JwtClaims;
import club.muimi.backend.security.jwt.JwtTokenService;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.period.PeriodService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.support.mail.MailService;
import club.muimi.backend.support.redis.AuthCacheService;
import club.muimi.backend.vo.auth.CurrentUserVo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private RecruitmentGroupRepository recruitmentGroupRepository;
    @Mock
    private PeriodService periodService;
    @Mock
    private AuthCacheService authCacheService;
    @Mock
    private MailService mailService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private AuthCookieService authCookieService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;
    private AuthProperties authProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.getEmailCode().setTtlSeconds(300);
        authProperties.getEmailCode().setSendCooldownSeconds(60);
        authProperties.getEmailCode().setMaxVerifyFailCount(5);
        authProperties.getEmailCode().setVerifyLockSeconds(300);
        authProperties.getEmailCode().setIpSendWindowSeconds(3600);
        authProperties.getEmailCode().setMaxIpSendCount(30);
        authProperties.getEmailCode().setGlobalSendWindowSeconds(60);
        authProperties.getEmailCode().setMaxGlobalSendCount(300);
        authProperties.getLogin().setMaxFailCount(5);
        authProperties.getLogin().setFailLockSeconds(900);
        rebuildAuthService();
    }

    private void rebuildAuthService() {
        authService = new AuthService(
                userRepository,
                groupMemberRepository,
                recruitmentGroupRepository,
                periodService,
                authCacheService,
                mailService,
                passwordEncoder,
                jwtTokenService,
                authCookieService,
                authProperties,
                currentUserService,
                auditLogService
        );
    }

    @Test
    void registerShouldCreateFreshmanUserWhenCodeValid() {
        RegisterRequest request = new RegisterRequest(
                "zhangsan",
                "user@example.com",
                "Pass1234",
                "Pass1234",
                "123456"
        );
        doNothing().when(periodService).ensureRegistrationOpen();
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(authCacheService.getEmailCode(EmailCodeScene.REGISTER, request.email())).thenReturn(Optional.of("123456"));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        var result = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.FRESHMAN);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getEmailVerified()).isTrue();
        assertThat(passwordEncoder.matches("Pass1234", savedUser.getPasswordHash())).isTrue();
        assertThat(result.id()).isEqualTo(1L);
        verify(authCacheService).deleteEmailCode(EmailCodeScene.REGISTER, request.email());
    }

    @Test
    void loginShouldWriteCookieWhenCredentialsCorrect() {
        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("Pass1234"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .build();
        when(authCacheService.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtTokenService.generateToken(user, true)).thenReturn("token-value");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        var result = authService.login(new LoginRequest(user.getEmail(), "Pass1234", true), request, new MockHttpServletResponse());

        assertThat(result.email()).isEqualTo(user.getEmail());
        verify(authCacheService).clearLoginFailCount(anyString());
        verify(authCookieService).writeLoginCookie(any(MockHttpServletResponse.class), anyString(), anyBoolean());
        verify(authCookieService).writeCsrfCookie(any(MockHttpServletResponse.class), anyString());
    }

    @Test
    void loginShouldIgnoreForwardedHeadersWhenProxyTrustDisabled() {
        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("Pass1234"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .build();
        when(authCacheService.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(authCacheService.incrementLoginFailCount(anyString(), any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.8");
        request.addHeader("X-Real-IP", "203.0.113.9");

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong", false), request, new MockHttpServletResponse()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("邮箱或密码错误");

        ArgumentCaptor<String> throttleKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(authCacheService).incrementLoginFailCount(throttleKeyCaptor.capture(), any());
        assertThat(throttleKeyCaptor.getValue()).isEqualTo("user@example.com|127.0.0.1");
    }

    @Test
    void loginShouldUseForwardedChainWhenRemoteAddrIsTrustedProxy() {
        authProperties.getLogin().setTrustForwardHeaders(true);
        authProperties.getLogin().setTrustedProxies(List.of("127.0.0.1", "198.51.100.20"));
        rebuildAuthService();

        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("Pass1234"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .build();
        when(authCacheService.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(authCacheService.incrementLoginFailCount(anyString(), any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.20");

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong", false), request, new MockHttpServletResponse()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("邮箱或密码错误");

        ArgumentCaptor<String> throttleKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(authCacheService).incrementLoginFailCount(throttleKeyCaptor.capture(), any());
        assertThat(throttleKeyCaptor.getValue()).isEqualTo("user@example.com|203.0.113.10");
    }

    @Test
    void loginShouldFallbackToRemoteAddrWhenForwardedChainInvalid() {
        authProperties.getLogin().setTrustForwardHeaders(true);
        authProperties.getLogin().setTrustedProxies(List.of("127.0.0.1"));
        rebuildAuthService();

        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("Pass1234"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .build();
        when(authCacheService.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(authCacheService.incrementLoginFailCount(anyString(), any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "not-an-ip");
        request.addHeader("X-Real-IP", "also-not-an-ip");

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong", false), request, new MockHttpServletResponse()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("邮箱或密码错误");

        ArgumentCaptor<String> throttleKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(authCacheService).incrementLoginFailCount(throttleKeyCaptor.capture(), any());
        assertThat(throttleKeyCaptor.getValue()).isEqualTo("user@example.com|127.0.0.1");
    }

    @Test
    void loginShouldRecordFailureWhenPasswordIncorrect() {
        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("Pass1234"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .build();
        when(authCacheService.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(authCacheService.incrementLoginFailCount(anyString(), any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong", false), request, new MockHttpServletResponse()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("邮箱或密码错误");

        verify(authCacheService).incrementLoginFailCount(anyString(), any());
    }

    @Test
    void loginShouldReturnGenericUnauthorizedWhenAccountDisabled() {
        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("Pass1234"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.DISABLED)
                .tokenVersion(0L)
                .build();
        when(authCacheService.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(authCacheService.incrementLoginFailCount(anyString(), any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "Pass1234", false), request, new MockHttpServletResponse()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("邮箱或密码错误");

        verify(authCacheService).incrementLoginFailCount(anyString(), any());
        verify(authCacheService, never()).clearLoginFailCount(anyString());
    }

    @Test
    void changePasswordShouldIncreaseTokenVersionAndClearCookie() {
        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("OldPass123"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(3L)
                .build();
        LoginUser loginUser = new LoginUser(1L, "zhangsan", "user@example.com", user.getPasswordHash(), Role.FRESHMAN, UserStatus.ACTIVE, 3L, "jti-1");
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(currentUserService.requireCurrentUser()).thenReturn(loginUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(authCookieService.resolveToken(httpServletRequest)).thenReturn(Optional.of("token"));
        when(jwtTokenService.parse("token")).thenReturn(new JwtClaims(1L, "FRESHMAN", 3L, "jti-1", Instant.now().plusSeconds(600)));
        when(jwtTokenService.remainingValidity(any())).thenReturn(Duration.ofMinutes(10));

        authService.changePassword(
                new ChangePasswordRequest("OldPass123", "NewPass123", "NewPass123"),
                httpServletRequest,
                new MockHttpServletResponse()
        );

        assertThat(user.getTokenVersion()).isEqualTo(4L);
        assertThat(passwordEncoder.matches("NewPass123", user.getPasswordHash())).isTrue();
        verify(authCacheService).blacklistToken(anyString(), any());
        verify(authCookieService).clearLoginCookie(any(MockHttpServletResponse.class));
    }

    @Test
    void resetPasswordShouldUpdatePasswordAndDeleteVerificationCode() {
        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("OldPass123"))
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(2L)
                .build();
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "654321", "NewPass123", "NewPass123");
        when(authCacheService.getEmailCode(EmailCodeScene.RESET_PASSWORD, request.email())).thenReturn(Optional.of("654321"));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));

        authService.resetPassword(request);

        assertThat(user.getTokenVersion()).isEqualTo(3L);
        assertThat(passwordEncoder.matches("NewPass123", user.getPasswordHash())).isTrue();
        verify(authCacheService).deleteEmailCode(EmailCodeScene.RESET_PASSWORD, request.email());
    }

    @Test
    void sendEmailCodeShouldNotLeakWhetherResetAccountExists() {
        SendEmailCodeRequest request = new SendEmailCodeRequest("missing@example.com", EmailCodeScene.RESET_PASSWORD);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(authCacheService.hasEmailCooldown(request.scene(), request.email())).thenReturn(false);

        authService.sendEmailCode(request);

        verify(authCacheService).markEmailCooldown(any(), anyString(), any());
    }

    @Test
    void sendEmailCodeShouldNotLeakWhetherRegisterAccountExists() {
        SendEmailCodeRequest request = new SendEmailCodeRequest("exists@example.com", EmailCodeScene.REGISTER);
        doNothing().when(periodService).ensureRegistrationOpen();
        when(userRepository.existsByEmail(request.email())).thenReturn(true);
        when(authCacheService.hasEmailCooldown(request.scene(), request.email())).thenReturn(false);

        authService.sendEmailCode(request);

        verify(authCacheService).markEmailCooldown(request.scene(), request.email(), Duration.ofSeconds(60));
        verify(mailService, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void sendEmailCodeShouldRejectWhenIpRateLimitExceeded() {
        SendEmailCodeRequest request = new SendEmailCodeRequest("user@example.com", EmailCodeScene.REGISTER);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("203.0.113.10");
        when(authCacheService.incrementEmailSendIpCount(eq("203.0.113.10"), any())).thenReturn(31L);

        assertThatThrownBy(() -> authService.sendEmailCode(request, httpRequest))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("验证码发送过于频繁，请稍后再试");

        verify(authCacheService, never()).incrementEmailSendGlobalCount(any());
        verify(mailService, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void sendEmailCodeShouldRejectWhenGlobalRateLimitExceeded() {
        SendEmailCodeRequest request = new SendEmailCodeRequest("user@example.com", EmailCodeScene.REGISTER);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("203.0.113.10");
        when(authCacheService.incrementEmailSendIpCount(eq("203.0.113.10"), any())).thenReturn(1L);
        when(authCacheService.incrementEmailSendGlobalCount(any())).thenReturn(301L);

        assertThatThrownBy(() -> authService.sendEmailCode(request, httpRequest))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("验证码发送服务繁忙，请稍后再试");

        verify(mailService, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void sendEmailCodeShouldRollbackCacheWhenMailSendFails() {
        SendEmailCodeRequest request = new SendEmailCodeRequest("exists@example.com", EmailCodeScene.RESET_PASSWORD);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);
        when(authCacheService.hasEmailCooldown(request.scene(), request.email())).thenReturn(false);
        doThrow(new IllegalStateException("smtp down"))
                .when(mailService)
                .sendVerificationCode(anyString(), anyString(), any());

        assertThatThrownBy(() -> authService.sendEmailCode(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("smtp down");

        verify(authCacheService).deleteEmailCode(request.scene(), request.email());
        verify(authCacheService).clearEmailCooldown(request.scene(), request.email());
    }

    @Test
    void registerShouldTranslateUniqueConstraintConflictToBusinessConflict() {
        RegisterRequest request = new RegisterRequest(
                "zhangsan",
                "user@example.com",
                "Pass1234",
                "Pass1234",
                "123456"
        );
        doNothing().when(periodService).ensureRegistrationOpen();
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(authCacheService.getEmailCode(EmailCodeScene.REGISTER, request.email())).thenReturn(Optional.of("123456"));
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("用户名或邮箱已存在");

        verify(authCacheService, never()).deleteEmailCode(EmailCodeScene.REGISTER, request.email());
    }

    @Test
    void registerShouldLockVerificationAttemptsWhenWrongCodeReachedLimit() {
        RegisterRequest request = new RegisterRequest(
                "zhangsan",
                "user@example.com",
                "Pass1234",
                "Pass1234",
                "000000"
        );
        doNothing().when(periodService).ensureRegistrationOpen();
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(authCacheService.isEmailCodeVerifyLocked(EmailCodeScene.REGISTER, request.email())).thenReturn(false);
        when(authCacheService.getEmailCode(EmailCodeScene.REGISTER, request.email())).thenReturn(Optional.of("123456"));
        when(authCacheService.incrementEmailCodeVerifyFailCount(any(), anyString(), any())).thenReturn(5L);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(club.muimi.backend.exception.ValidationException.class)
                .hasMessage("验证码错误");

        verify(authCacheService).deleteEmailCode(EmailCodeScene.REGISTER, request.email());
        verify(authCacheService).lockEmailCodeVerify(any(), anyString(), any());
    }

    @Test
    void registerShouldRejectWhenVerificationCodeAlreadyLocked() {
        RegisterRequest request = new RegisterRequest(
                "zhangsan",
                "user@example.com",
                "Pass1234",
                "Pass1234",
                "123456"
        );
        doNothing().when(periodService).ensureRegistrationOpen();
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(authCacheService.isEmailCodeVerifyLocked(EmailCodeScene.REGISTER, request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("验证码错误次数过多，请稍后重新获取");
    }

    @Test
    void getCurrentUserShouldReturnEmptyGroupsForAdminWithoutMembership() {
        User admin = User.builder()
                .id(99L)
                .username("admin")
                .email("admin@example.com")
                .passwordHash("hashed")
                .emailVerified(true)
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .build();
        LoginUser loginUser = new LoginUser(99L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        when(currentUserService.requireCurrentUser()).thenReturn(loginUser);
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(groupMemberRepository.findAllByUserId(99L)).thenReturn(List.of());
        when(recruitmentGroupRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(99L)).thenReturn(List.of());

        CurrentUserVo result = authService.getCurrentUser();

        assertThat(result.role()).isEqualTo(Role.ADMIN);
        assertThat(result.groups()).isEmpty();
        assertThat(result.leaderGroups()).isEmpty();
    }
}
