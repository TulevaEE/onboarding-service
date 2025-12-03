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
public class SavingFundWithdrawalApplicationDetails implements ApplicationDetails {

  private final UUID id;
  private final BigDecimal amount;
  private final Currency currency;
  private final String iban;
  private final Instant cancellationDeadline;
  private final Instant fulfillmentDeadline;

  @Builder.Default private ApplicationType type = ApplicationType.SAVING_FUND_WITHDRAWAL;

  public SavingFundWithdrawalApplicationDetails(
      UUID id,
      BigDecimal amount,
      Currency currency,
      String iban,
      Instant cancellationDeadline,
      Instant fulfillmentDeadline,
      ApplicationType type) {
    validate(type);
    this.id = id;
    this.amount = amount;
    this.currency = currency;
    this.iban = iban;
    this.cancellationDeadline = cancellationDeadline;
    this.fulfillmentDeadline = fulfillmentDeadline;
    this.type = type;
  }

  @Override
  public Integer getPillar() {
    return null;
  }

  private void validate(ApplicationType type) {
    if (type != ApplicationType.SAVING_FUND_WITHDRAWAL) {
      throw new IllegalArgumentException("Invalid ApplicationType: type=" + type);
    }
  }
}
