package ee.tuleva.onboarding.analytics.fund;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsFundTransactionRepository extends JpaRepository<FundTransaction, Long> {

  boolean existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
      LocalDate transactionDate,
      String personalId,
      String transactionType,
      BigDecimal amount,
      BigDecimal unitAmount);

  @Query("SELECT MAX(aft.transactionDate) FROM FundTransaction aft")
  Optional<LocalDate> findLatestTransactionDate();
}
