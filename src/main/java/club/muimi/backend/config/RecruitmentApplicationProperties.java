package club.muimi.backend.config;

import club.muimi.backend.common.enums.Grade;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.recruitment.application")
public class RecruitmentApplicationProperties {

    private Set<Grade> allowedGrades = EnumSet.of(Grade.YEAR_1, Grade.YEAR_2);

    @PostConstruct
    public void validate() {
        if (allowedGrades == null || allowedGrades.isEmpty()) {
            throw new IllegalStateException("app.recruitment.application.allowed-grades 不能为空");
        }
        if (allowedGrades.contains(null)) {
            throw new IllegalStateException("app.recruitment.application.allowed-grades 不能包含空值");
        }
    }
}
