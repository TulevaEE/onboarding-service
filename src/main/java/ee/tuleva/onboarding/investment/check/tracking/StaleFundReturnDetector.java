package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.dailyReturn;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class StaleFundReturnDetector {

  private final TrackingDifferenceEventRepository eventRepository;
  private final FundNavQueryService fundNavQueryService;
  private final PublicHolidays publicHolidays;

  List<LocalDate> staleCheckDates(TulevaFund fund, LocalDate from, LocalDate to) {
    return eventRepository.findDeduplicatedEventsForPeriod(fund, MODEL_PORTFOLIO, from, to).stream()
        .filter(this::navMovedSinceTheCheckRan)
        .map(TrackingDifferenceEvent::getCheckDate)
        .toList();
  }

  private boolean navMovedSinceTheCheckRan(TrackingDifferenceEvent event) {
    var currentReturn = fundReturnFromTheCurrentNav(event);
    if (currentReturn.isEmpty() || currentReturn.get().compareTo(event.getFundReturn()) == 0) {
      return false;
    }
    log.info(
        "Stored fund return no longer matches the NAV series: fund={}, checkDate={}, storedFundReturn={}, currentFundReturn={}",
        event.getFund(),
        event.getCheckDate(),
        event.getFundReturn(),
        currentReturn.get());
    return true;
  }

  private Optional<BigDecimal> fundReturnFromTheCurrentNav(TrackingDifferenceEvent event) {
    var fundCode = event.getFund().getCode();
    var checkDate = event.getCheckDate();
    var previousNav =
        fundNavQueryService
            .findLatestNavPerUnit(fundCode, publicHolidays.previousWorkingDay(checkDate))
            .filter(nav -> nav.signum() != 0);
    return fundNavQueryService
        .findLatestNavPerUnit(fundCode, checkDate)
        .flatMap(nav -> previousNav.map(previous -> dailyReturn(nav, previous)));
  }
}
