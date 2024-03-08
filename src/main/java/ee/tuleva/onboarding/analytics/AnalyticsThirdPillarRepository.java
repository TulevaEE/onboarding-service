package ee.tuleva.onboarding.analytics;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalyticsThirdPillarRepository extends JpaRepository<AnalyticsThirdPillar, Long> {

  @Query(
      "SELECT record FROM AnalyticsThirdPillar record WHERE record.reportingDate = (SELECT MAX(history.reportingDate) FROM AnalyticsThirdPillar history)")
  List<AnalyticsThirdPillar> findRecordsWithMostRecentReportingDate();
}
