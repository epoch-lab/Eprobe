package club.muimi.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock appClock() {
        return Clock.system(APP_ZONE);
    }
}
