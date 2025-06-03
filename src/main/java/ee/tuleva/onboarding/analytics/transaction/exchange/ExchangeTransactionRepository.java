package ee.tuleva.onboarding.analytics.transaction.exchange;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {

  Optional<ExchangeTransaction> findTopByOrderByReportingDateDesc();

  List<ExchangeTransaction> findByReportingDate(LocalDate reportingDate);

  @Modifying
  @Query("DELETE FROM ExchangeTransaction t WHERE t.reportingDate = :reportingDate")
  int deleteByReportingDate(@Param("reportingDate") LocalDate reportingDate);
}
