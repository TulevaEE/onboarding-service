package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.cashbuffer.PayoutClass.CYCLE;
import static ee.tuleva.onboarding.investment.cashbuffer.PayoutClass.RECURRING;
import static ee.tuleva.onboarding.investment.cashbuffer.PayoutClass.TAIL;
import static ee.tuleva.onboarding.investment.cashbuffer.PayoutClass.UNRECOGNISED;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.ledger.RegistrarPayout;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

record MonthlyFlows(
    YearMonth month,
    BigDecimal inflow,
    BigDecimal recurringOutflow,
    BigDecimal tailOutflow,
    BigDecimal cycleOutflow,
    BigDecimal unrecognisedOutflow,
    int unrecognisedPayouts) {

  static MonthlyFlows of(YearMonth month, BigDecimal inflow, List<RegistrarPayout> payouts) {
    return new MonthlyFlows(
        month,
        cents(inflow),
        total(payouts, RECURRING),
        total(payouts, TAIL),
        total(payouts, CYCLE),
        total(payouts, UNRECOGNISED),
        (int) payouts.stream().filter(payout -> classOf(payout) == UNRECOGNISED).count());
  }

  BigDecimal operatingOutflow() {
    return recurringOutflow.add(tailOutflow);
  }

  private static BigDecimal total(List<RegistrarPayout> payouts, PayoutClass payoutClass) {
    return cents(
        payouts.stream()
            .filter(payout -> classOf(payout) == payoutClass)
            .map(RegistrarPayout::amount)
            .reduce(ZERO, BigDecimal::add));
  }

  private static PayoutClass classOf(RegistrarPayout payout) {
    return PayoutClass.of(payout.reason());
  }

  private static BigDecimal cents(BigDecimal amount) {
    return amount.setScale(2, HALF_UP);
  }
}
