package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

record SebFundConfirmation(
    TulevaFund fund,
    String tradeReference,
    Instant orderReceivedAt,
    LocalDate pricingDate,
    LocalDate valueDate,
    String isin,
    TransactionType side,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal tradeAmount,
    BigDecimal fee,
    BigDecimal totalAmount) {

  static final ZoneId SEB_TIME_ZONE = ZoneId.of("Europe/Tallinn");

  LocalDate orderReceivedOn() {
    return orderReceivedAt.atZone(SEB_TIME_ZONE).toLocalDate();
  }
}
