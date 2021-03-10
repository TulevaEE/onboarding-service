package ee.tuleva.onboarding.epis.application;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationResponse {
  private boolean successful;
  private Integer errorCode;
  private String errorMessage;
  private ApplicationType applicationType;
  private String processId;
}
