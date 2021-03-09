package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MandateResponseDTO {

    private List<ApplicationResponse> mandateResponses;

}
