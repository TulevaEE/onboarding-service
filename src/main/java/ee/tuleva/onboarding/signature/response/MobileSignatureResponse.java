package ee.tuleva.onboarding.signature.response;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
public class MobileSignatureResponse {

  @Nullable // During smartid signing start request
  private final String challengeCode;
}
