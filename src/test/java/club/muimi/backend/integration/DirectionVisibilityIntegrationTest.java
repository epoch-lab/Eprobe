package club.muimi.backend.integration;

import club.muimi.backend.entity.Direction;
import club.muimi.backend.repository.DirectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/fresh?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&sessionVariables=default_storage_engine=InnoDB",
        "spring.datasource.username=epoch",
        "spring.datasource.password=123456",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "spring.mail.username=test-mail@example.com",
        "spring.mail.password=test-auth-code",
        "app.security.jwt.secret=test-jwt-secret-key-with-at-least-32-bytes-long",
        "app.security.jwt.cookie-secure=false",
        "app.security.jwt.cookie-same-site=Lax",
        "app.auth.cache-type=redis",
        "app.bootstrap.default-admin.enabled=false"
})
class DirectionVisibilityIntegrationTest {

    private final List<Long> createdDirectionIds = new ArrayList<>();
    @Autowired
    private DirectionRepository directionRepository;
    @Autowired
    private WebApplicationContext webApplicationContext;

    @AfterEach
    void cleanUp() {
        for (int i = createdDirectionIds.size() - 1; i >= 0; i--) {
            Long directionId = createdDirectionIds.get(i);
            directionRepository.findById(directionId).ifPresent(directionRepository::delete);
        }
        createdDirectionIds.clear();
    }

    @Test
    void publicDirectionsEndpointShouldAlwaysHideDisabledDirections() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String suffix = String.valueOf(System.nanoTime());
        Direction enabledRoot = createDirection(null, "公开方向-" + suffix, 1, true);
        createDirection(enabledRoot.getId(), "公开子方向-" + suffix, 2, true);
        Direction disabledRoot = createDirection(null, "禁用方向-" + suffix, 1, false);
        createDirection(disabledRoot.getId(), "禁用子方向-" + suffix, 2, false);

        MvcResult result = mockMvc.perform(get("/api/v1/directions").param("enabled", "false"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("公开方向-" + suffix);
        assertThat(body).contains("公开子方向-" + suffix);
        assertThat(body).doesNotContain("禁用方向-" + suffix);
        assertThat(body).doesNotContain("禁用子方向-" + suffix);
    }

    private Direction createDirection(Long parentId, String name, int level, boolean enabled) {
        Direction direction = directionRepository.save(Direction.builder()
                .parentId(parentId)
                .name(name)
                .level(level)
                .sortOrder(1)
                .enabled(enabled)
                .build());
        createdDirectionIds.add(direction.getId());
        return direction;
    }
}
