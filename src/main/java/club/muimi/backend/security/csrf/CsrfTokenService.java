package club.muimi.backend.security.csrf;

import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.security.jwt.JwtClaims;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class CsrfTokenService {

    private static final String CSRF_SIGN_PREFIX = "csrf:";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final club.muimi.backend.config.JwtProperties jwtProperties;

    public CsrfTokenService(club.muimi.backend.config.JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String createToken(JwtClaims claims) {
        return createToken(claims.userId(), claims.tokenVersion(), claims.jti());
    }

    public String createToken(LoginUser loginUser) {
        return createToken(loginUser.getUserId(), loginUser.getTokenVersion(), loginUser.getTokenJti());
    }

    private String createToken(Long userId, Long tokenVersion, String tokenJti) {
        try {
            // 将用户身份、登录版本和 JWT 唯一标识绑定在一起，避免伪造固定 CSRF Token。
            String content = userId + ":" + tokenVersion + ":" + tokenJti;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(ensureKeyLength((CSRF_SIGN_PREFIX + jwtProperties.getSecret()).getBytes(StandardCharsets.UTF_8)), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("生成 CSRF Token 失败", exception);
        }
    }

    private byte[] ensureKeyLength(byte[] rawKey) {
        if (rawKey.length >= 32) {
            return rawKey;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawKey);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法初始化 CSRF 签名密钥", exception);
        }
    }
}
