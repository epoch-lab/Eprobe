package club.muimi.backend.config;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.entity.User;
import club.muimi.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultAdminInitializerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void shouldCreateDefaultAdminWhenNoAdminExists() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DefaultAdminProperties properties = buildProperties();
        DefaultAdminInitializer initializer = new DefaultAdminInitializer(userRepository, passwordEncoder, properties, jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), any(), any())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT RELEASE_LOCK(?)"), eq(Integer.class), any())).thenReturn(1);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByUsername(properties.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(properties.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldSkipInitializationWhenAdminAlreadyExists() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DefaultAdminProperties properties = buildProperties();
        DefaultAdminInitializer initializer = new DefaultAdminInitializer(userRepository, passwordEncoder, properties, jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), any(), any())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT RELEASE_LOCK(?)"), eq(Integer.class), any())).thenReturn(1);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldFailWhenNoAdminExistsAndConfigMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DefaultAdminProperties properties = new DefaultAdminProperties();
        DefaultAdminInitializer initializer = new DefaultAdminInitializer(userRepository, passwordEncoder, properties, jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), any(), any())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT RELEASE_LOCK(?)"), eq(Integer.class), any())).thenReturn(1);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEFAULT_ADMIN_USERNAME");
    }

    @Test
    void shouldCreateVerifiedAdminRoleUser() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DefaultAdminProperties properties = buildProperties();
        DefaultAdminInitializer initializer = new DefaultAdminInitializer(userRepository, passwordEncoder, properties, jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), any(), any())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT RELEASE_LOCK(?)"), eq(Integer.class), any())).thenReturn(1);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByUsername(properties.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(properties.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldFailWhenInitializationLockCannotBeAcquired() {
        UserRepository userRepository = mock(UserRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DefaultAdminProperties properties = buildProperties();
        DefaultAdminInitializer initializer = new DefaultAdminInitializer(userRepository, passwordEncoder, properties, jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("初始化锁");
    }

    private DefaultAdminProperties buildProperties() {
        DefaultAdminProperties properties = new DefaultAdminProperties();
        properties.setEnabled(true);
        properties.setUsername("bootstrap_admin");
        properties.setPassword("AdminPass123");
        properties.setEmail("bootstrap_admin@example.com");
        assertThat(properties.isConfigured()).isTrue();
        return properties;
    }
}
