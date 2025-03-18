package ee.tuleva.onboarding.analytics.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {

  boolean existsByReportingDateAndSecurityFromAndSecurityToAndCodeAndUnitAmountAndPercentage(
      LocalDate reportingDate,
      String securityFrom,
      String securityTo,
      String code,
      BigDecimal unitAmount,
      BigDecimal percentage);
}
