package ee.tuleva.onboarding.investment.check.rebalance;

import ee.tuleva.onboarding.investment.calendar.TradingCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;

record RebalanceRules(BigDecimal tolerance, long tradesDueMonths, long inLineDueMonths) {

  private static final int SISEKORD_4_P_2_6_1_TRADES_DUE_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE = 3;

  RebalanceDeadlines deadlinesFrom(LocalDate effectiveDate, TradingCalendar calendar) {
    return new RebalanceDeadlines(
        effectiveDate.plusMonths(tradesDueMonths),
        effectiveDate.plusMonths(inLineDueMonths),
        calendar.nextOrSameBusinessDay(
            effectiveDate.plusMonths(
                SISEKORD_4_P_2_6_1_TRADES_DUE_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE)));
  }
}
