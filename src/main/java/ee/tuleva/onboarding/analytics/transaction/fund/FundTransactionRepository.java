package ee.tuleva.onboarding.analytics.transaction.fund;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FundTransactionRepository extends JpaRepository<FundTransaction, Long> {

  @Modifying
  @Query(
      "DELETE FROM FundTransaction ft "
          + "WHERE ft.isin = :isin "
          + "AND ft.transactionDate >= :startDate "
          + "AND ft.transactionDate <= :endDate")
  int deleteByIsinAndTransactionDateBetween(
      @Param("isin") String isin,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Query("SELECT MAX(aft.transactionDate) FROM FundTransaction aft WHERE aft.isin = :isin")
  Optional<LocalDate> findLatestTransactionDateByIsin(@Param("isin") String isin);
}
