package club.muimi.backend.config;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.entity.User;
import club.muimi.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final String INIT_LOCK_NAME = "fresh:bootstrap:default-admin";
    private static final int INIT_LOCK_TIMEOUT_SECONDS = 10;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DefaultAdminProperties defaultAdminProperties;
    private final JdbcTemplate jdbcTemplate;

    public DefaultAdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DefaultAdminProperties defaultAdminProperties,
            JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultAdminProperties = defaultAdminProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!defaultAdminProperties.isEnabled()) {
            log.info("默认管理员初始化已关闭。");
            return;
        }

        acquireInitializationLock();
        try {
            if (userRepository.existsByRole(Role.ADMIN)) {
                log.info("数据库中已存在管理员用户，跳过默认管理员初始化。");
                return;
            }

            defaultAdminProperties.validateRequired();
            validateNoConflict();

            User admin = User.builder()
                    .username(defaultAdminProperties.getUsername())
                    .email(defaultAdminProperties.getEmail())
                    .passwordHash(passwordEncoder.encode(defaultAdminProperties.getPassword()))
                    // 默认管理员由部署配置直接创建，不需要邮件验证流程。
                    .emailVerified(true)
                    .role(Role.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .tokenVersion(0L)
                    .lastLoginAt(null)
                    .build();
            userRepository.save(admin);
            log.warn("已自动初始化默认管理员用户：username={}, email={}", admin.getUsername(), admin.getEmail());
        } finally {
            releaseInitializationLock();
        }
    }

    private void validateNoConflict() {
        if (userRepository.existsByUsername(defaultAdminProperties.getUsername())) {
            throw new IllegalStateException("默认管理员用户名已被占用，请调整 DEFAULT_ADMIN_USERNAME");
        }
        if (userRepository.existsByEmail(defaultAdminProperties.getEmail())) {
            throw new IllegalStateException("默认管理员邮箱已被占用，请调整 DEFAULT_ADMIN_EMAIL");
        }
    }

    private void acquireInitializationLock() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                INIT_LOCK_NAME,
                INIT_LOCK_TIMEOUT_SECONDS
        );
        if (!Integer.valueOf(1).equals(result)) {
            throw new IllegalStateException("无法获取默认管理员初始化锁，请稍后重试启动");
        }
    }

    private void releaseInitializationLock() {
        try {
            jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, INIT_LOCK_NAME);
        } catch (Exception exception) {
            log.warn("释放默认管理员初始化锁失败，lock={}", INIT_LOCK_NAME, exception);
        }
    }
}
