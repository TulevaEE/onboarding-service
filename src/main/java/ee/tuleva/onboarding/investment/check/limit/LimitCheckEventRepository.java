package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface LimitCheckEventRepository extends JpaRepository<LimitCheckEvent, Long> {

  List<LimitCheckEvent> findByFundAndCheckDate(TulevaFund fund, LocalDate checkDate);

  void deleteByFundAndCheckDate(TulevaFund fund, LocalDate checkDate);
}
