package ee.tuleva.onboarding.mandate.application;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalApplicationDetails implements ApplicationDetails {

  private final String depositAccountIBAN;
  private final Instant cancellationDeadline;
  private final LocalDate fulfillmentDate;

  @Override
  public Integer getPillar() {
    return 2;
  }
}
