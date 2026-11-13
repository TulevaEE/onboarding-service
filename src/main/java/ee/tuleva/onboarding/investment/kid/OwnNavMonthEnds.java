package ee.tuleva.onboarding.investment.kid;

import static java.time.temporal.ChronoUnit.MONTHS;

import ee.tuleva.onboarding.investment.series.SeriesSegment;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

record OwnNavMonthEnds(@Nullable YearMonth firstMonth) {

  private static final double MONTHS_PER_YEAR = 12.0;

  static OwnNavMonthEnds of(TulevaFund fund, List<SeriesSegment> series) {
    return series.stream()
        .filter(segment -> segment.key().equals(fund.getIsin()))
        .findFirst()
        .map(OwnNavMonthEnds::firstWholeMonth)
        .map(OwnNavMonthEnds::new)
        .orElseGet(() -> new OwnNavMonthEnds(null));
  }

  private static YearMonth firstWholeMonth(SeriesSegment ownNav) {
    var from = ownNav.from();
    if (from == null || from.getDayOfMonth() != 1) {
      throw new KidCalculationRefusedException(
          "The fund's own NAV must start on a month's first day: key=%s, from=%s"
              .formatted(ownNav.key(), from));
    }
    return YearMonth.from(from);
  }

  List<MonthEndPrice> grossedUp(List<MonthEndPrice> monthEnds, double ongoingCharges) {
    if (firstMonth == null) {
      return monthEnds;
    }
    var lastMonthBeforeTheOwnNav = firstMonth.minusMonths(1);
    return monthEnds.stream()
        .map(
            monthEnd -> {
              var monthsPaidByTheFund =
                  Math.max(
                      0,
                      MONTHS.between(
                          lastMonthBeforeTheOwnNav, YearMonth.from(monthEnd.monthEnd())));
              return new MonthEndPrice(
                  monthEnd.monthEnd(),
                  monthEnd.price()
                      / Math.pow(1 - ongoingCharges, monthsPaidByTheFund / MONTHS_PER_YEAR));
            })
        .toList();
  }
}
