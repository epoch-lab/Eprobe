package club.muimi.backend.security.cookie;

import club.muimi.backend.config.JwtProperties;
import club.muimi.backend.security.csrf.CsrfTokenService;
import club.muimi.backend.security.jwt.JwtClaims;
import club.muimi.backend.security.jwt.JwtTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@Service
public class AuthCookieService {

    private final JwtProperties jwtProperties;
    private final JwtTokenService jwtTokenService;
    private final CsrfTokenService csrfTokenService;

    public AuthCookieService(
            JwtProperties jwtProperties,
            JwtTokenService jwtTokenService,
            CsrfTokenService csrfTokenService
    ) {
        this.jwtProperties = jwtProperties;
        this.jwtTokenService = jwtTokenService;
        this.csrfTokenService = csrfTokenService;
    }

    public void writeLoginCookie(HttpServletResponse response, String token, boolean rememberMe) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .sameSite(jwtProperties.getCookieSameSite())
                .maxAge(jwtTokenService.resolveExpireSeconds(rememberMe))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void writeCsrfCookie(HttpServletResponse response, String token) {
        JwtClaims claims = jwtTokenService.parse(token);
        long maxAgeSeconds = Math.max(jwtTokenService.remainingValidity(claims).getSeconds(), 0L);
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCsrfCookieName(), csrfTokenService.createToken(claims))
                .httpOnly(false)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .sameSite(jwtProperties.getCookieSameSite())
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearLoginCookie(HttpServletResponse response) {
        ResponseCookie authCookie = ResponseCookie.from(jwtProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .sameSite(jwtProperties.getCookieSameSite())
                .maxAge(0)
                .build();
        ResponseCookie csrfCookie = ResponseCookie.from(jwtProperties.getCsrfCookieName(), "")
                .httpOnly(false)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .sameSite(jwtProperties.getCookieSameSite())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    public Optional<String> resolveToken(HttpServletRequest request) {
        return resolveCookieValue(request, jwtProperties.getCookieName());
    }

    public Optional<String> resolveCsrfToken(HttpServletRequest request) {
        return resolveCookieValue(request, jwtProperties.getCsrfCookieName());
    }

    private Optional<String> resolveCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
