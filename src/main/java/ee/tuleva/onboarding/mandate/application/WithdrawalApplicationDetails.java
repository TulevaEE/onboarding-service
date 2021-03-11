package ee.tuleva.onboarding.mandate.application;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalApplicationDetails implements ApplicationDetails {
  private final String depositAccountIBAN;
  private final LocalDate cancellationDeadline;
  private final LocalDate fulfillmentDate;
}
