package ee.tuleva.onboarding.investment.check.rebalance;

import ee.tuleva.onboarding.investment.calendar.TradingCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;

record RebalanceRules(BigDecimal tolerance, long warningCalendarDays) {

  private static final int SISEKORD_4_P_2_6_1_REBALANCED_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE = 3;

  static LocalDate deadlineFrom(LocalDate effectiveDate, TradingCalendar calendar) {
    return calendar.nextOrSameBusinessDay(
        effectiveDate.plusMonths(
            SISEKORD_4_P_2_6_1_REBALANCED_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE));
  }

  LocalDate warningFrom(LocalDate deadline) {
    return deadline.minusDays(warningCalendarDays);
  }
}
