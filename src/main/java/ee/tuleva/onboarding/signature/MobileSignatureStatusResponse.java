package ee.tuleva.onboarding.signature;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@AllArgsConstructor
public class MobileSignatureStatusResponse {

  private final SignatureStatus statusCode;
  private final @Nullable String challengeCode;
}
