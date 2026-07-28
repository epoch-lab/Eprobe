package club.muimi.backend.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseMigrationConfig {

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlywayPostProcessor() {
        return DatabaseMigrationConfig::configureEntityManagerFactoryDependency;
    }

    private static void configureEntityManagerFactoryDependency(ConfigurableListableBeanFactory beanFactory) {
        if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
            beanFactory.getBeanDefinition("entityManagerFactory").setDependsOn("flyway");
        }
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}
