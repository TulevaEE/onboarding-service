package ee.tuleva.onboarding.mandate.response;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
public class MobileIdSignatureResponse {

	private final String mobileIdChallengeCode;

}
