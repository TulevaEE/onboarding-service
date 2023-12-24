package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.PAYMENT_RATE;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentRateApplicationDetails implements ApplicationDetails {

  private BigDecimal paymentRate;
  private final Instant cancellationDeadline;
  private final LocalDate fulfillmentDate;
  @Builder.Default private ApplicationType type = PAYMENT_RATE;

  public PaymentRateApplicationDetails(
      BigDecimal paymentRate,
      Instant cancellationDeadline,
      LocalDate fulfillmentDate,
      ApplicationType type) {
    validate(type);
    this.paymentRate = paymentRate;
    this.cancellationDeadline = cancellationDeadline;
    this.fulfillmentDate = fulfillmentDate;
    this.type = type;
  }

  private void validate(ApplicationType type) {
    if (type != PAYMENT_RATE) {
      throw new IllegalArgumentException("Invalid ApplicationType: type=" + type);
    }
  }

  @Override
  public Integer getPillar() {
    return 2;
  }
}
