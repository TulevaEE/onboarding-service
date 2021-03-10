package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationResponseDTO {
  private List<ApplicationResponse> mandateResponses;
}
