package ee.tuleva.onboarding.payment.recurring;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record RecurringPaymentRequest(
    String accountNumber,
    String recipientName,
    @Nullable BigDecimal amount,
    @Nullable Currency currency,
    String description,
    String reference,
    PaymentInterval interval,
    LocalDate firstPaymentDate) {
  public enum PaymentInterval {
    MONTHLY
  }
}
