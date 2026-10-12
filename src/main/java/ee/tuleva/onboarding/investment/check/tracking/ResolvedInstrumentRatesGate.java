package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.investment.fees.rate.InstrumentOcfService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ResolvedInstrumentRatesGate {

  private final InstrumentOcfService instrumentOcfService;
  private final TrackingDifferenceNotifier notifier;

  void requireResolvedFor(TulevaFund fund, LocalDate periodEnd) {
    var rateMonth = YearMonth.from(periodEnd);
    if (!instrumentOcfService.hasRatesResolvedAfterItClosed(rateMonth)) {
      throw new InstrumentRatesNotResolvedException(
          "Instrument rates not resolved for the month, run InstrumentFeeRateJob first: fund="
              + fund
              + ", month="
              + rateMonth);
    }
  }

  boolean admitsEveryFund(LocalDate periodStart, LocalDate periodEnd) {
    var rateMonth = YearMonth.from(periodEnd);
    if (instrumentOcfService.hasRatesResolvedAfterItClosed(rateMonth)) {
      return true;
    }
    refuseEveryFund(periodStart, periodEnd, rateMonth);
    return false;
  }

  private void refuseEveryFund(LocalDate periodStart, LocalDate periodEnd, YearMonth rateMonth) {
    var funds = List.of(TulevaFund.values());
    log.error(
        "TD attribution refused, instrument rates not resolved: funds={}, period={}-{}, month={}",
        funds,
        periodStart,
        periodEnd,
        rateMonth);
    notifier.notifyAttributionRefusedForUnresolvedRates(funds, periodStart, periodEnd, rateMonth);
  }
}
