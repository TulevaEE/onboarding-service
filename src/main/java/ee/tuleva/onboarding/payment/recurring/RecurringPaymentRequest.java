package ee.tuleva.onboarding.payment.recurring;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringPaymentRequest(
    String accountNumber,
    String recipientName,
    BigDecimal amount,
    Currency currency,
    String description,
    String reference,
    PaymentInterval interval,
    LocalDate firstPaymentDate) {
  public enum PaymentInterval {
    MONTHLY
  }
}
