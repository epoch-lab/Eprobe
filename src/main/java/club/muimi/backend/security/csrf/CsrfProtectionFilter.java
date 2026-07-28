package club.muimi.backend.security.csrf;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.common.api.ErrorCode;
import club.muimi.backend.config.JwtProperties;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.security.cookie.AuthCookieService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/send-email-code",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    );

    private final AuthCookieService authCookieService;
    private final CsrfTokenService csrfTokenService;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public CsrfProtectionFilter(
            AuthCookieService authCookieService,
            CsrfTokenService csrfTokenService,
            JwtProperties jwtProperties,
            ObjectMapper objectMapper
    ) {
        this.authCookieService = authCookieService;
        this.csrfTokenService = csrfTokenService;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        // 仅拦截需要登录且会修改状态的 API，请求页面和公开接口时不强制要求 CSRF 头。
        return !uri.startsWith("/api/") || PUBLIC_API_PATHS.contains(uri);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerToken = request.getHeader(jwtProperties.getCsrfHeaderName());
        String cookieToken = authCookieService.resolveCsrfToken(request).orElse(null);
        String expectedToken = csrfTokenService.createToken(loginUser);

        // 双重提交校验：请求头和 Cookie 都必须携带与当前登录态绑定的同一个 CSRF Token。
        if (isInvalid(headerToken) || isInvalid(cookieToken) || !matches(expectedToken, headerToken) || !matches(expectedToken, cookieToken)) {
            writeForbiddenResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInvalid(String token) {
        return token == null || token.isBlank();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(ErrorCode.FORBIDDEN, "CSRF 校验失败", null)
        );
    }
}
