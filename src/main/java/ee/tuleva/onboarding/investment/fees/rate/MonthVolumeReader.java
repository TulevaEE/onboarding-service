package ee.tuleva.onboarding.investment.fees.rate;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MonthVolumeReader {

  private final FundNavQueryService fundNavQueryService;

  Optional<MonthVolume> volumeOf(String isin, List<TulevaFund> funds, YearMonth month) {
    var navDatesNewestFirst = publishedNavDatesNewestFirst(funds, month);
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

  private List<LocalDate> publishedNavDatesNewestFirst(List<TulevaFund> funds, YearMonth month) {
    return funds.stream()
        .flatMap(
            fund ->
                fundNavQueryService
                    .findPublishedNavDatesBetween(
                        fund.getCode(), month.atDay(1), month.atEndOfMonth())
                    .stream())
        .distinct()
        .sorted(Comparator.reverseOrder())
        .toList();
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
