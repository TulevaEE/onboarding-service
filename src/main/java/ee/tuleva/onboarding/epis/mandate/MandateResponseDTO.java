package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.mandate.MandateApplicationType;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MandateResponseDTO {

  List<MandateResponse> mandateResponses;

  @Getter
  @Setter
  public static class MandateResponse {
    private boolean successful;
    private Integer errorCode;
    private String errorMessage;
    private MandateApplicationType applicationType;
    private String processId;
  }
}
