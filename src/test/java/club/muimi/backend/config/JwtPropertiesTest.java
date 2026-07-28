package club.muimi.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    @Test
    void shouldRejectWeakOrMissingSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("short-secret");
        properties.setExpireSeconds(7200);
        properties.setRememberExpireSeconds(2592000);
        properties.setCookieName("lab_recruit_token");
        properties.setCsrfCookieName("XSRF-TOKEN");
        properties.setCsrfHeaderName("X-CSRF-TOKEN");
        properties.setCookieSecure(true);
        properties.setCookieSameSite("Strict");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT 密钥长度不能少于 32");
    }
}
