package ee.tuleva.onboarding.savings.fund.application;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.mandate.application.ApplicationDetails;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavingFundPaymentApplicationDetails implements ApplicationDetails {

  private final BigDecimal amount;
  private final Currency currency;
  private final UUID paymentId;
  private final Instant cancellationDeadline;
  private final Instant fulfillmentDeadline;

  @Builder.Default private ApplicationType type = ApplicationType.SAVING_FUND_PAYMENT;

  public SavingFundPaymentApplicationDetails(
      BigDecimal amount,
      Currency currency,
      UUID paymentId,
      Instant cancellationDeadline,
      Instant fulfillmentDeadline,
      ApplicationType type) {
    validate(type);
    this.amount = amount;
    this.currency = currency;
    this.paymentId = paymentId;
    this.cancellationDeadline = cancellationDeadline;
    this.fulfillmentDeadline = fulfillmentDeadline;
    this.type = type;
  }

  @Override
  public Integer getPillar() {
    // TODO: Decide what we want to return here
    return null;
  }

  private void validate(ApplicationType type) {
    if (type != ApplicationType.SAVING_FUND_PAYMENT) {
      throw new IllegalArgumentException("Invalid ApplicationType: type=" + type);
    }
  }
}
