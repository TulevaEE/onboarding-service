package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

interface LimitCheckEventRepository extends JpaRepository<LimitCheckEvent, Long> {

  List<LimitCheckEvent> findByFundAndCheckDate(TulevaFund fund, LocalDate checkDate);

  @Modifying
  @Transactional
  void deleteByFundAndCheckDateAndCheckType(
      TulevaFund fund, LocalDate checkDate, CheckType checkType);
}
