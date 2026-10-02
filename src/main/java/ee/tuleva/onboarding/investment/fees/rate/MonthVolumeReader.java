package ee.tuleva.onboarding.investment.fees.rate;

import static java.math.BigDecimal.ZERO;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MonthVolumeReader {

  private final FundNavQueryService fundNavQueryService;
  private final PublicHolidays publicHolidays;

  Optional<MonthVolume> volumeOf(String isin, List<TulevaFund> funds, YearMonth month) {
    var publishedNavDates = publishedNavDatesByFund(funds, month);
    requireTheLastNavOfEachFundThatPublishedInTheMonth(publishedNavDates, month);
    var navDatesNewestFirst =
        publishedNavDates.values().stream()
            .flatMap(List::stream)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();
    if (navDatesNewestFirst.isEmpty()) {
      return Optional.empty();
    }
    return navDatesNewestFirst.stream()
        .map(
            navDate ->
                holdingOn(isin, funds, navDate).map(amount -> new MonthVolume(amount, navDate)))
        .filter(volume -> volume.isEmpty() || volume.get().amount().signum() != 0)
        .findFirst()
        .orElseGet(() -> Optional.of(MonthVolume.notHeldDuringTheMonth()));
  }

  private Map<TulevaFund, List<LocalDate>> publishedNavDatesByFund(
      List<TulevaFund> funds, YearMonth month) {
    return funds.stream()
        .collect(
            toMap(
                identity(),
                fund ->
                    fundNavQueryService.findPublishedNavDatesBetween(
                        fund.getCode(), month.atDay(1), month.atEndOfMonth()),
                (first, second) -> first,
                LinkedHashMap::new));
  }

  private void requireTheLastNavOfEachFundThatPublishedInTheMonth(
      Map<TulevaFund, List<LocalDate>> publishedNavDates, YearMonth month) {
    var lastNavDate = publicHolidays.previousWorkingDay(month.plusMonths(1).atDay(1));
    publishedNavDates.forEach(
        (fund, navDates) -> {
          if (!navDates.isEmpty() && !navDates.contains(lastNavDate)) {
            throw new MonthNavNotYetPublishedException(fund, month, lastNavDate);
          }
        });
  }

  private Optional<BigDecimal> holdingOn(String isin, List<TulevaFund> funds, LocalDate navDate) {
    var calculations =
        funds.stream()
            .map(fund -> fundNavQueryService.findPublishedCalculation(fund.getCode(), navDate))
            .toList();
    if (calculations.stream().anyMatch(Optional::isEmpty)) {
      return Optional.empty();
    }
    return Optional.of(
        calculations.stream()
            .flatMap(Optional::stream)
            .flatMap(calculation -> calculation.securityLines().stream())
            .filter(line -> isin.equals(line.accountId()))
            .map(NavAccountLine::value)
            .reduce(ZERO, BigDecimal::add));
  }
}
