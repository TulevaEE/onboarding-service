package ee.tuleva.onboarding.mandate.response;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
public class MobileSignatureResponse {

    @Deprecated
    private final String mobileIdChallengeCode;
    private final String challengeCode;

}
