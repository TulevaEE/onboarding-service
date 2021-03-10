package ee.tuleva.onboarding.mandate.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MobileSignatureStatusResponse {

  private final String statusCode;
  private final String challengeCode;
}
