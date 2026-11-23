package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;

record RecordedSebFundConfirmation(
    String tradeReference,
    TulevaFund fund,
    String isin,
    TransactionType side,
    Instant orderReceivedAt,
    LocalDate pricingDate,
    String sourceFileKey) {

  static RecordedSebFundConfirmation of(SebFundConfirmationUpload upload) {
    SebFundConfirmation confirmation = upload.confirmation();
    return new RecordedSebFundConfirmation(
        confirmation.tradeReference(),
        confirmation.fund(),
        confirmation.isin(),
        confirmation.side(),
        confirmation.orderReceivedAt(),
        confirmation.pricingDate(),
        upload.key());
  }

  boolean isTheTradeOf(TransactionOrder order) {
    return fund == order.getFund()
        && isin.equals(order.getInstrumentIsin())
        && side == order.getTransactionType()
        && order.getInstrumentType() == FUND;
  }

  boolean saysTheSameAs(RecordedSebFundConfirmation other) {
    return tradeReference.equals(other.tradeReference)
        && fund == other.fund
        && isin.equals(other.isin)
        && side == other.side
        && orderReceivedAt.equals(other.orderReceivedAt)
        && pricingDate.equals(other.pricingDate);
  }
}
