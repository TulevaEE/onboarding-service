package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.KidRefusalCause.NO_PRICES;
import static ee.tuleva.onboarding.investment.kid.KidRefusalCause.SERIES_STOPPED;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record MonthEndPrices(TulevaFund fund, @Nullable LocalDate latestClose, LocalDate today) {

  static String noPrices(TulevaFund fund) {
    return NO_PRICES.reason("fund=" + fund);
  }

  boolean closes(YearMonth month) {
    var close = latestClose;
    return close != null && !month.isAfter(ScenarioPeriod.lastClosedMonth(close, today));
  }

  Optional<String> missingCloseReason(YearMonth month) {
    if (closes(month)) {
      return Optional.empty();
    }
    return stoppedReason()
        .or(
            () ->
                Optional.of(
                    "Month-end close not in yet: fund=%s, month=%s, latestClose=%s"
                        .formatted(fund, month, latestClose)));
  }

  Optional<String> stoppedReason() {
    var close = latestClose;
    if (close == null) {
      return Optional.of(noPrices(fund));
    }
    return ScenarioPeriod.seriesStopped(close, today)
        ? Optional.of(SERIES_STOPPED.reason("fund=%s, latestClose=%s".formatted(fund, close)))
        : Optional.empty();
  }
}
