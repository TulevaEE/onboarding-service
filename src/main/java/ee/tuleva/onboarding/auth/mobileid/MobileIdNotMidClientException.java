package ee.tuleva.onboarding.auth.mobileid;

import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;

public class MobileIdNotMidClientException extends MobileIdException {
  public MobileIdNotMidClientException() {
    super(
        ofSingleError(
            "mobile.id.certificates.revoked",
            "You are not a Mobile-ID client or your Mobile-ID certificates are revoked. Please contact your mobile operator."));
  }
}
