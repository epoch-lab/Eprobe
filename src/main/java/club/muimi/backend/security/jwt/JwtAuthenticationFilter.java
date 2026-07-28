package club.muimi.backend.security.jwt;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.common.api.ErrorCode;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.UnauthorizedException;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.security.cookie.AuthCookieService;
import club.muimi.backend.support.redis.AuthCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthCookieService authCookieService;
    private final JwtTokenService jwtTokenService;
    private final AuthCacheService authCacheService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            AuthCookieService authCookieService,
            JwtTokenService jwtTokenService,
            AuthCacheService authCacheService,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.authCookieService = authCookieService;
        this.jwtTokenService = jwtTokenService;
        this.authCacheService = authCacheService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            if (!authenticateFromCookie(request, response)) {
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean authenticateFromCookie(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<String> tokenOptional = authCookieService.resolveToken(request);
        if (tokenOptional.isEmpty()) {
            return true;
        }

        try {
            JwtClaims claims = jwtTokenService.parse(tokenOptional.get());
            if (authCacheService.isTokenBlacklisted(claims.jti())) {
                clearContextAndCookie(response);
                return true;
            }

            User user = userRepository.findById(claims.userId()).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.getTokenVersion().equals(claims.tokenVersion())) {
                clearContextAndCookie(response);
                return true;
            }

            // 这里同时校验 tokenVersion，确保改密/重置密码后旧 Token 立即失效。
            LoginUser loginUser = new LoginUser(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getPasswordHash(),
                    user.getRole(),
                    user.getStatus(),
                    user.getTokenVersion(),
                    claims.jti()
            );
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    loginUser,
                    null,
                    loginUser.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (UnauthorizedException exception) {
            clearContextAndCookie(response);
            return true;
        } catch (RuntimeException exception) {
            log.error("JWT 鉴权链路发生基础设施异常，请检查 Redis 或数据库状态", exception);
            SecurityContextHolder.clearContext();
            writeServiceUnavailableResponse(response);
            return false;
        }
    }

    private void clearContextAndCookie(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        authCookieService.clearLoginCookie(response);
    }

    private void writeServiceUnavailableResponse(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(ErrorCode.INTERNAL_ERROR.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(ErrorCode.INTERNAL_ERROR, "认证服务暂时不可用，请稍后再试", null)
        );
    }
}
