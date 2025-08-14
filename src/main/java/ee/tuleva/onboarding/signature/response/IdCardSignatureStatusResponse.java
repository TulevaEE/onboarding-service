package ee.tuleva.onboarding.signature.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class IdCardSignatureStatusResponse {

  private final SignatureStatus statusCode;
}
