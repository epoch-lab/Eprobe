package club.muimi.backend.security.jwt;

import club.muimi.backend.config.JwtProperties;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public JwtTokenService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    public String generateToken(User user, boolean rememberMe) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(resolveExpireSeconds(rememberMe));
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", String.valueOf(user.getId()));
            payload.put("role", user.getRole().name());
            payload.put("tokenVersion", user.getTokenVersion());
            payload.put("jti", UUID.randomUUID().toString());
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());

            String headerPart = encodeJson(header);
            String payloadPart = encodeJson(payload);
            String content = headerPart + "." + payloadPart;
            return content + "." + sign(content);
        } catch (Exception exception) {
            throw new IllegalStateException("生成 JWT 失败", exception);
        }
    }

    public JwtClaims parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new UnauthorizedException();
            }
            String content = parts[0] + "." + parts[1];
            String expectedSignature = sign(content);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new UnauthorizedException();
            }

            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            long expiresAt = longValue(payload.get("exp"));
            if (Instant.ofEpochSecond(expiresAt).isBefore(Instant.now())) {
                throw new UnauthorizedException();
            }
            return new JwtClaims(
                    Long.parseLong(String.valueOf(payload.get("sub"))),
                    String.valueOf(payload.get("role")),
                    longValue(payload.get("tokenVersion")),
                    String.valueOf(payload.get("jti")),
                    Instant.ofEpochSecond(expiresAt)
            );
        } catch (Exception exception) {
            throw new UnauthorizedException();
        }
    }

    public Duration remainingValidity(JwtClaims claims) {
        Instant now = Instant.now();
        if (!claims.expiresAt().isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, claims.expiresAt());
    }

    public long resolveExpireSeconds(boolean rememberMe) {
        return rememberMe ? jwtProperties.getRememberExpireSeconds() : jwtProperties.getExpireSeconds();
    }

    private String encodeJson(Map<String, Object> payload) throws Exception {
        byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(ensureKeyLength(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)), "HmacSHA256"));
        byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private byte[] ensureKeyLength(byte[] rawKey) {
        if (rawKey.length >= 32) {
            return rawKey;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawKey);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法初始化 JWT 签名密钥", exception);
        }
    }
}
