package ee.tuleva.onboarding.investment.check.rebalance;

import ee.tuleva.onboarding.investment.calendar.TradingCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;

record RebalanceRules(BigDecimal tolerance, long warningCalendarDays) {

  private static final int SISEKORD_4_P_2_6_1_REBALANCED_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE = 3;
  private static final int CUSTODIAN_CONFIRMS_A_TRADE_WITHIN_WORKING_DAYS = 2;

  static LocalDate deadlineFrom(LocalDate effectiveDate, TradingCalendar calendar) {
    return calendar.nextOrSameBusinessDay(
        effectiveDate.plusMonths(
            SISEKORD_4_P_2_6_1_REBALANCED_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE));
  }

  static LocalDate awaitingExecutionUntil(LocalDate deadline, TradingCalendar calendar) {
    return calendar.addBusinessDays(deadline, CUSTODIAN_CONFIRMS_A_TRADE_WITHIN_WORKING_DAYS);
  }

  LocalDate warningFrom(LocalDate deadline) {
    return deadline.minusDays(warningCalendarDays);
  }
}
