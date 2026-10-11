package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.investment.epis.PevaRavaCycle;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlowService;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class RedemptionCycleLookup {

  private final PevaRavaPeriodService periodService;
  private final PevaRavaFlowService flowService;

  RedemptionCycleHint resolve(TulevaFund fund, LocalDate checkDate) {
    if (!isExecutionDate(checkDate)) {
      return RedemptionCycleHint.ordinaryDay();
    }
    try {
      var flows = flowService.calculateFlows(checkDate).get(fund);
      if (flows == null) {
        return RedemptionCycleHint.executionDateWithoutFigures();
      }
      return new RedemptionCycleHint(true, flows.ravaEur(), flows.pikEur());
    } catch (Exception e) {
      log.warn(
          "PEVA/RAVA figures unavailable, reporting the execution date alone: fund={}, checkDate={}",
          fund,
          checkDate,
          e);
      return RedemptionCycleHint.executionDateWithoutFigures();
    }
  }

  private boolean isExecutionDate(LocalDate checkDate) {
    try {
      return periodService.executionPeriods(checkDate.getYear()).stream()
          .map(PevaRavaCycle::execDate)
          .anyMatch(checkDate::equals);
    } catch (Exception e) {
      log.warn("PEVA/RAVA execution dates unavailable: checkDate={}", checkDate, e);
      return false;
    }
  }
}
