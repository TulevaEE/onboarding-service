package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsThirdPillarTransactionRepository
    extends JpaRepository<AnalyticsThirdPillarTransaction, Long> {

  @Query("SELECT MAX(t.reportingDate) FROM AnalyticsThirdPillarTransaction t")
  Optional<LocalDate> findLatestReportingDate();

  List<AnalyticsThirdPillarTransaction> findByReportingDateGreaterThanEqual(
      LocalDate reportingDate);

  @Modifying
  @Query(
      "DELETE FROM AnalyticsThirdPillarTransaction t WHERE t.reportingDate BETWEEN :startDate AND :endDate")
  int deleteByReportingDateBetween(
      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
