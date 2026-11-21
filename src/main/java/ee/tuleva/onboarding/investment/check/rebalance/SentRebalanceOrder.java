package ee.tuleva.onboarding.investment.check.rebalance;

import ee.tuleva.onboarding.investment.calendar.TradingCalendar;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record SentRebalanceOrder(String isin, LocalDate sentOn, LocalDate awaitedThrough) {

  private static final int
      AN_ORDER_WITHOUT_A_SETTLEMENT_DATE_SETTLES_WORKING_DAYS_AFTER_IT_IS_SENT = 2;
  private static final int THE_CUSTODIANS_REPORT_FOR_A_DAY_ARRIVES_WORKING_DAYS_LATER = 1;

  static SentRebalanceOrder of(
      String isin,
      LocalDate sentOn,
      @Nullable LocalDate expectedSettlementDate,
      TradingCalendar calendar) {
    var settlementDate =
        expectedSettlementDate != null
            ? expectedSettlementDate
            : calendar.addBusinessDays(
                sentOn, AN_ORDER_WITHOUT_A_SETTLEMENT_DATE_SETTLES_WORKING_DAYS_AFTER_IT_IS_SENT);
    return new SentRebalanceOrder(
        isin,
        sentOn,
        calendar.addBusinessDays(
            calendar.nextOrSameBusinessDay(settlementDate),
            THE_CUSTODIANS_REPORT_FOR_A_DAY_ARRIVES_WORKING_DAYS_LATER));
  }

  boolean confirmationOverdueOn(LocalDate date) {
    return date.isAfter(awaitedThrough);
  }
}
