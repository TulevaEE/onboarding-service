package ee.tuleva.onboarding.signature;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MobileSignatureStatusResponse {

  private final SignatureStatus statusCode;
  private final String challengeCode;
}
