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
import java.util.function.BiFunction;
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
        .filter(this::publishedNavMovedSinceTheCheckRan)
        .map(TrackingDifferenceEvent::getCheckDate)
        .toList();
  }

  private boolean publishedNavMovedSinceTheCheckRan(TrackingDifferenceEvent event) {
    var publishedReturn = fundReturn(event, fundNavQueryService::findPublishedNavPerUnit);
    if (publishedReturn.isEmpty() || publishedReturn.get().compareTo(event.getFundReturn()) == 0) {
      return false;
    }
    if (!aRecheckWouldStore(publishedReturn.get(), event)) {
      log.warn(
          "Stored fund return no longer matches the published NAV, but a recheck would read a later unpublished calculation, so the date is left alone: fund={}, checkDate={}, storedFundReturn={}, publishedFundReturn={}",
          event.getFund(),
          event.getCheckDate(),
          event.getFundReturn(),
          publishedReturn.get());
      return false;
    }
    log.info(
        "Stored fund return no longer matches the published NAV: fund={}, checkDate={}, storedFundReturn={}, publishedFundReturn={}",
        event.getFund(),
        event.getCheckDate(),
        event.getFundReturn(),
        publishedReturn.get());
    return true;
  }

  private boolean aRecheckWouldStore(BigDecimal publishedReturn, TrackingDifferenceEvent event) {
    return fundReturn(event, fundNavQueryService::findLatestNavPerUnit)
        .filter(returnARecheckReads -> returnARecheckReads.compareTo(publishedReturn) == 0)
        .isPresent();
  }

  private Optional<BigDecimal> fundReturn(
      TrackingDifferenceEvent event,
      BiFunction<String, LocalDate, Optional<BigDecimal>> navPerUnitOn) {
    var fundCode = event.getFund().getCode();
    var checkDate = event.getCheckDate();
    var previousNav =
        navPerUnitOn
            .apply(fundCode, publicHolidays.previousWorkingDay(checkDate))
            .filter(nav -> nav.signum() != 0);
    return navPerUnitOn
        .apply(fundCode, checkDate)
        .flatMap(nav -> previousNav.map(previous -> dailyReturn(nav, previous)));
  }
}
