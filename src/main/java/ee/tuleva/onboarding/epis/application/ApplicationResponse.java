package ee.tuleva.onboarding.epis.application;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponse {
  private boolean successful;
  private Integer errorCode;
  private String errorMessage;
  private ApplicationType applicationType;
  private String processId;
}
