package ee.tuleva.onboarding.analytics;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalyticsThirdPillarRepository extends JpaRepository<AnalyticsThirdPillar, Long> {

  List<AnalyticsThirdPillar> findAllByReportingDateBetween(LocalDateTime start, LocalDateTime end);

  @Query(
      "SELECT record FROM AnalyticsThirdPillar record WHERE record.reportingDate = (SELECT MAX(history.reportingDate) FROM AnalyticsThirdPillar history)")
  List<AnalyticsThirdPillar> findAllWithMostRecentReportingDate();
}
