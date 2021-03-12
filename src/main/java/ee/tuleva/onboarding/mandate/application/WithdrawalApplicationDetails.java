package ee.tuleva.onboarding.mandate.application;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalApplicationDetails implements ApplicationDetails {
  private final String depositAccountIBAN;
}
