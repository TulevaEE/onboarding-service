package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

public record R16FundFlow(
    TulevaFund fund,
    BigDecimal fondimaksedUnits,
    BigDecimal uhekordsedUnits,
    BigDecimal totalOutflowEur,
    LocalDate paymentMonth,
    LocalDate paymentDeadline,
    LocalDate sellByDate,
    BigDecimal fondimaksedEur,
    BigDecimal uhekordsedEur) {

  public R16PaymentDates paymentDates() {
    return new R16PaymentDates(paymentMonth, paymentDeadline, sellByDate);
  }
}
