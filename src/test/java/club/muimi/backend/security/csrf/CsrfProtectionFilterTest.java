package club.muimi.backend.security.csrf;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.config.JwtProperties;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.security.cookie.AuthCookieService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CsrfProtectionFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBlockUnsafeRequestWhenCsrfHeaderMissing() throws ServletException, IOException {
        JwtProperties jwtProperties = buildJwtProperties();
        AuthCookieService authCookieService = mock(AuthCookieService.class);
        CsrfTokenService csrfTokenService = new CsrfTokenService(jwtProperties);
        CsrfProtectionFilter filter = new CsrfProtectionFilter(authCookieService, csrfTokenService, jwtProperties, new ObjectMapper());
        LoginUser loginUser = new LoginUser(1L, "zhangsan", "user@example.com", "hashed", Role.FRESHMAN, UserStatus.ACTIVE, 2L, "jti-1");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(loginUser, null, loginUser.getAuthorities())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authCookieService.resolveCsrfToken(request)).thenReturn(Optional.empty());

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF 校验失败");
    }

    @Test
    void shouldAllowUnsafeRequestWhenCsrfHeaderAndCookieMatch() throws ServletException, IOException {
        JwtProperties jwtProperties = buildJwtProperties();
        AuthCookieService authCookieService = mock(AuthCookieService.class);
        CsrfTokenService csrfTokenService = new CsrfTokenService(jwtProperties);
        CsrfProtectionFilter filter = new CsrfProtectionFilter(authCookieService, csrfTokenService, jwtProperties, new ObjectMapper());
        LoginUser loginUser = new LoginUser(1L, "zhangsan", "user@example.com", "hashed", Role.FRESHMAN, UserStatus.ACTIVE, 2L, "jti-1");
        String csrfToken = csrfTokenService.createToken(loginUser);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(loginUser, null, loginUser.getAuthorities())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader(jwtProperties.getCsrfHeaderName(), csrfToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authCookieService.resolveCsrfToken(request)).thenReturn(Optional.of(csrfToken));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldSkipPublicLoginRequest() throws ServletException, IOException {
        JwtProperties jwtProperties = buildJwtProperties();
        AuthCookieService authCookieService = mock(AuthCookieService.class);
        CsrfTokenService csrfTokenService = new CsrfTokenService(jwtProperties);
        CsrfProtectionFilter filter = new CsrfProtectionFilter(authCookieService, csrfTokenService, jwtProperties, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(authCookieService);
    }

    private JwtProperties buildJwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-key-with-at-least-32-bytes-long");
        properties.setExpireSeconds(7200);
        properties.setRememberExpireSeconds(2592000);
        properties.setCookieName("lab_recruit_token");
        properties.setCsrfCookieName("XSRF-TOKEN");
        properties.setCsrfHeaderName("X-CSRF-TOKEN");
        properties.setCookieSecure(false);
        properties.setCookieSameSite("Lax");
        properties.validate();
        return properties;
    }
}
