package ee.tuleva.onboarding.analytics.exchange;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {

  @Modifying
  @Query("DELETE FROM ExchangeTransaction t WHERE t.reportingDate = :reportingDate")
  int deleteByReportingDate(@Param("reportingDate") LocalDate reportingDate);
}
