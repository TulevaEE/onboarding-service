package ee.tuleva.onboarding.analytics.thirdpillar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalyticsThirdPillarTransactionRepository
    extends JpaRepository<AnalyticsThirdPillarTransaction, Long> {
  boolean existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
      LocalDate reportingDate,
      String personalId,
      String transactionType,
      BigDecimal transactionValue,
      BigDecimal shareAmount);

  @Query("SELECT MAX(t.reportingDate) FROM AnalyticsThirdPillarTransaction t")
  Optional<LocalDate> findLatestReportingDate();
}
