package ee.tuleva.onboarding.analytics.thirdpillar;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsThirdPillarTransactionRepository
    extends JpaRepository<AnalyticsThirdPillarTransaction, Long> {
  boolean existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
      LocalDate reportingDate,
      String personalId,
      String transactionType,
      BigDecimal transactionValue,
      BigDecimal shareAmount);
}
