package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface LimitCheckEventRepository extends JpaRepository<LimitCheckEvent, Long> {

  List<LimitCheckEvent> findByFundAndCheckDate(TulevaFund fund, LocalDate checkDate);

  @Query(
      """
      SELECT DISTINCT e.checkDate FROM LimitCheckEvent e
      WHERE e.fund = :fund AND e.checkDate BETWEEN :start AND :end
      AND e.checkType IN :checkTypes
      """)
  List<LocalDate> findDistinctCheckDatesOfTypes(
      TulevaFund fund, LocalDate start, LocalDate end, Set<CheckType> checkTypes);

  default List<LocalDate> findDistinctCheckDates(TulevaFund fund, LocalDate start, LocalDate end) {
    return findDistinctCheckDatesOfTypes(fund, start, end, CheckType.WRITTEN_BY_THE_DAILY_CHECK);
  }

  List<LimitCheckEvent> findByFundAndCheckTypeAndCheckDateBetween(
      TulevaFund fund, CheckType checkType, LocalDate start, LocalDate end);

  @Transactional
  void deleteByFundAndCheckDateAndCheckType(
      TulevaFund fund, LocalDate checkDate, CheckType checkType);
}
