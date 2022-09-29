package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.PAYMENT;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.fund.ApiFundResponse;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentApplicationDetails implements ApplicationDetails {

  private final BigDecimal amount;
  private final Currency currency;
  private final ApiFundResponse targetFund;

  @Builder.Default private ApplicationType type = PAYMENT;

  public PaymentApplicationDetails(
      BigDecimal amount, Currency currency, ApiFundResponse targetFund, ApplicationType type) {
    validate(type);
    this.amount = amount;
    this.currency = currency;
    this.targetFund = targetFund;
    this.type = type;
  }

  private void validate(ApplicationType type) {
    if (type != PAYMENT) {
      throw new IllegalArgumentException("Invalid ApplicationType: type=" + type);
    }
  }

  @Override
  public Integer getPillar() {
    return targetFund.getPillar();
  }
}
