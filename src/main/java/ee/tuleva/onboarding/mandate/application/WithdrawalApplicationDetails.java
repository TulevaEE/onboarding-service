package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalApplicationDetails implements ApplicationDetails {

  private final String depositAccountIBAN;
  private final Instant cancellationDeadline;
  private final LocalDate fulfillmentDate;
  private final ApplicationType type;

  public WithdrawalApplicationDetails(
      String depositAccountIBAN,
      Instant cancellationDeadline,
      LocalDate fulfillmentDate,
      ApplicationType type) {
    validate(type);
    this.depositAccountIBAN = depositAccountIBAN;
    this.cancellationDeadline = cancellationDeadline;
    this.fulfillmentDate = fulfillmentDate;
    this.type = type;
  }

  private void validate(ApplicationType type) {
    if (type == null || !Set.of(WITHDRAWAL, EARLY_WITHDRAWAL).contains(type)) {
      throw new IllegalArgumentException("Invalid ApplicationType: type=" + type);
    }
  }

  @Override
  public Integer getPillar() {
    return 2;
  }
}
