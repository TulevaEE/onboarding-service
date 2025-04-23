package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FundBalanceRepository extends JpaRepository<FundBalance, Long> {

  @Modifying
  @Query("DELETE FROM FundBalance fb WHERE fb.requestDate = :requestDate")
  int deleteByRequestDate(@Param("requestDate") LocalDate requestDate);

  @Query("SELECT MAX(fb.requestDate) FROM FundBalance fb")
  Optional<LocalDate> findLatestRequestDate();
}
