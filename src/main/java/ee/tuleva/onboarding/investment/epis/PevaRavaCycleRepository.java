package ee.tuleva.onboarding.investment.epis;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PevaRavaCycleRepository extends JpaRepository<PevaRavaCycleEntity, Long> {

  Optional<PevaRavaCycleEntity> findByExecDate(LocalDate execDate);

  @Query(
      """
      SELECT new ee.tuleva.onboarding.investment.epis.PevaRavaCycleReports(
          c.r17ReportId, c.r21ReportId)
      FROM PevaRavaCycleEntity c
      WHERE c.execDate = :execDate
      """)
  Optional<PevaRavaCycleReports> findReportsByExecDate(@Param("execDate") LocalDate execDate);

  Optional<PevaRavaCycleEntity> findTopByExecDateGreaterThanEqualOrderByExecDateAsc(LocalDate date);
}
