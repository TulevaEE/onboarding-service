package ee.tuleva.onboarding.epis.mandate;

import static java.util.Collections.singletonList;

import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApplicationResponseDTO {

  private List<ApplicationResponse> mandateResponses;

  public ApplicationResponseDTO(ApplicationResponse applicationResponse) {
    this.mandateResponses = singletonList(applicationResponse);
  }
}
