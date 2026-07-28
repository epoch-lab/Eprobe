package club.muimi.backend.security.jwt;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.entity.User;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.cookie.AuthCookieService;
import club.muimi.backend.support.redis.AuthCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private AuthCookieService authCookieService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private AuthCacheService authCacheService;
    @Mock
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPopulateSecurityContextWhenTokenIsValid() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                authCookieService,
                jwtTokenService,
                authCacheService,
                userRepository,
                new ObjectMapper()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = User.builder()
                .id(1L)
                .username("zhangsan")
                .email("user@example.com")
                .passwordHash("hashed")
                .emailVerified(true)
                .role(Role.FRESHMAN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(2L)
                .build();
        JwtClaims claims = new JwtClaims(1L, "FRESHMAN", 2L, "jti-1", Instant.now().plusSeconds(300));

        when(authCookieService.resolveToken(request)).thenReturn(Optional.of("token"));
        when(jwtTokenService.parse("token")).thenReturn(claims);
        when(authCacheService.isTokenBlacklisted("jti-1")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isInstanceOf(club.muimi.backend.security.auth.LoginUser.class);
        assertThat(((club.muimi.backend.security.auth.LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getTokenJti()).isEqualTo("jti-1");
        verify(authCookieService, never()).clearLoginCookie(response);
    }

    @Test
    void shouldClearCookieWhenTokenIsBlacklisted() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                authCookieService,
                jwtTokenService,
                authCacheService,
                userRepository,
                new ObjectMapper()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JwtClaims claims = new JwtClaims(1L, "FRESHMAN", 2L, "jti-1", Instant.now().plusSeconds(300));

        when(authCookieService.resolveToken(request)).thenReturn(Optional.of("token"));
        when(jwtTokenService.parse("token")).thenReturn(claims);
        when(authCacheService.isTokenBlacklisted("jti-1")).thenReturn(true);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authCookieService).clearLoginCookie(response);
    }

    @Test
    void shouldReturn500WhenInfrastructureDependencyFails() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                authCookieService,
                jwtTokenService,
                authCacheService,
                userRepository,
                new ObjectMapper()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JwtClaims claims = new JwtClaims(1L, "FRESHMAN", 2L, "jti-1", Instant.now().plusSeconds(300));

        when(authCookieService.resolveToken(request)).thenReturn(Optional.of("token"));
        when(jwtTokenService.parse("token")).thenReturn(claims);
        when(authCacheService.isTokenBlacklisted("jti-1")).thenThrow(new IllegalStateException("redis down"));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authCookieService, never()).clearLoginCookie(response);
    }
}
