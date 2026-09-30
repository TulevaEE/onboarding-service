package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TdAttributionSelfHeal {

  private final TdAttributionStaleness staleness;
  private final ResolvedInstrumentRatesGate rateGate;
  private final PeriodicTdAttributionService service;
  private final TrackingDifferenceNotifier notifier;

  void rewriteStaleMonths(List<YearMonth> months) {
    months.forEach(this::rewriteStaleFundsWithoutStoppingLaterMonths);
  }

  private void rewriteStaleFundsWithoutStoppingLaterMonths(YearMonth month) {
    try {
      rewriteStaleFunds(month);
    } catch (Exception e) {
      log.error("TD attribution self-heal could not judge a month: period={}", month, e);
    }
  }

  private void rewriteStaleFunds(YearMonth month) {
    var staleFunds =
        Arrays.stream(TulevaFund.values())
            .filter(fund -> staleness.needsRewrite(fund, month))
            .toList();
    if (staleFunds.isEmpty() || !rateGate.admitsEveryFund(month.atDay(1), month.atEndOfMonth())) {
      return;
    }
    staleFunds.forEach(fund -> rewriteReportingFailure(fund, month));
  }

  private void rewriteReportingFailure(TulevaFund fund, YearMonth month) {
    log.info("Rewriting stale TD attribution: fund={}, period={}", fund, month);
    try {
      service.computeAttribution(fund, month.atDay(1), month.atEndOfMonth(), MONTHLY);
    } catch (AttributionPeriodStillStaleException e) {
      log.error(
          "TD attribution not rewritten, already reported: fund={}, period={}", fund, month, e);
    } catch (Exception e) {
      log.error("Failed to rewrite stale TD attribution: fund={}, period={}", fund, month, e);
      notifier.notifyAttributionFailed(
          fund, month.atDay(1), month.atEndOfMonth(), e.getClass().getSimpleName());
    }
  }
}
