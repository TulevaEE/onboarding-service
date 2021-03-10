package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Application {
  private Long id;
  private ApplicationType type;
  private ApplicationStatus status;
  private ApplicationDetails details;
}
