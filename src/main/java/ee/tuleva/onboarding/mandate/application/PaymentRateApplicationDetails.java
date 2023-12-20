package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.PAYMENT_RATE;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;

import ee.tuleva.onboarding.fund.ApiFundResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentRateApplicationDetails implements ApplicationDetails {

  private final ApiFundResponse sourceFund;
  private final Instant cancellationDeadline;
  private final LocalDate fulfillmentDate;
  private BigDecimal rate;
  @Builder.Default private ApplicationType type = TRANSFER;

  public PaymentRateApplicationDetails(
      ApiFundResponse sourceFund,
      Instant cancellationDeadline,
      LocalDate fulfillmentDate,
      BigDecimal rate,
      ApplicationType type) {
    validate(type);
    this.sourceFund = sourceFund;
    this.cancellationDeadline = cancellationDeadline;
    this.fulfillmentDate = fulfillmentDate;
    this.rate = rate;
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
