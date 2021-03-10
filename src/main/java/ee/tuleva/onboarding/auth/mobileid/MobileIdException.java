package ee.tuleva.onboarding.auth.mobileid;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.List;

public class MobileIdException extends ErrorsResponseException {

  public MobileIdException(ErrorsResponse errorsResponse) {
    super(errorsResponse);
  }

  public static MobileIdException ofErrors(List<String> errors) {
    ErrorsResponse errorsResponse = new ErrorsResponse();
    for (int i = 0; i < errors.size(); i++) {
      errorsResponse.add(
          ErrorResponse.builder().code("mobile.id.error." + i).message(errors.get(i)).build());
    }
    return new MobileIdException(errorsResponse);
  }
}
