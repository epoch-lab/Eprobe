package club.muimi.backend.repository;

import club.muimi.backend.common.enums.PeriodType;
import club.muimi.backend.entity.RecruitmentPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecruitmentPeriodRepository extends JpaRepository<RecruitmentPeriod, Long> {

    Optional<RecruitmentPeriod> findByPeriodTypeAndEnabledTrue(PeriodType periodType);

    List<RecruitmentPeriod> findAllByEnabledTrue();
}
