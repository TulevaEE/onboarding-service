package ee.tuleva.onboarding.signature.response;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
public class IdCardSignatureResponse {

  private final String hash;
}
