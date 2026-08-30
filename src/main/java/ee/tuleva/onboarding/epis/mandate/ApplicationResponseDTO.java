package ee.tuleva.onboarding.epis.mandate;

import static java.util.Collections.singletonList;

import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@NoArgsConstructor
public class ApplicationResponseDTO {

  @Nullable private List<ApplicationResponse> mandateResponses;

  public ApplicationResponseDTO(ApplicationResponse applicationResponse) {
    this.mandateResponses = singletonList(applicationResponse);
  }

  public MandateProcessResult toProcessResult() {
    return LegacyMandateSubmissionMapper.toProcessResult(mandateResponses);
  }
}
