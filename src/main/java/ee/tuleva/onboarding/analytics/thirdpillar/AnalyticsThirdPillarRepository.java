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
          + "WHERE record.personalCode IN ("
          + "    SELECT DISTINCT intermediate.personalCode "
          + "    FROM AnalyticsThirdPillar intermediate "
          + "    WHERE intermediate.reportingDate > :startDate AND intermediate.reportingDate < :endDate "
          + "    AND intermediate.personalCode NOT IN ("
          + "        SELECT DISTINCT initial.personalCode "
          + "        FROM AnalyticsThirdPillar initial "
          + "        WHERE initial.reportingDate = :startDate"
          + "    ) "
          + "    AND intermediate.personalCode NOT IN ("
          + "        SELECT DISTINCT final.personalCode "
          + "        FROM AnalyticsThirdPillar final "
          + "        WHERE final.reportingDate = :endDate"
          + "    )"
          + ")")
  List<AnalyticsThirdPillar> findIntermediateEntries(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
