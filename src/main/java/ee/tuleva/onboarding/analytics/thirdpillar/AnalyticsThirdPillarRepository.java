package ee.tuleva.onboarding.analytics.thirdpillar;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsThirdPillarRepository extends JpaRepository<AnalyticsThirdPillar, Long> {

  List<AnalyticsThirdPillar> findAllByReportingDateBetween(LocalDateTime start, LocalDateTime end);

  @Query(
      "SELECT record FROM AnalyticsThirdPillar record WHERE record.reportingDate = (SELECT MAX(history.reportingDate) FROM AnalyticsThirdPillar history)")
  List<AnalyticsThirdPillar> findAllWithMostRecentReportingDate();

  @Query(
      "SELECT record "
          + "FROM AnalyticsThirdPillar record "
          + "WHERE record.reportingDate >= :startDate AND record.reportingDate <= :endDate "
          + "AND record.personalCode = :personalCode")
  List<AnalyticsThirdPillar> findByDateRangeAndPersonalCode(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("personalCode") String personalCode);
}
