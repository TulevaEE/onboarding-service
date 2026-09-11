package ee.tuleva.onboarding.auth.smartid;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.List;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class SmartIdException extends ErrorsResponseException {

  private final @Nullable SmartIdLoginError loginError;

  public SmartIdException(ErrorsResponse errorsResponse) {
    super(errorsResponse);
    this.loginError = null;
  }

  public SmartIdException(SmartIdLoginError loginError) {
    super(loginError.toErrorsResponse());
    this.loginError = loginError;
  }

  public static SmartIdException ofErrors(List<String> errors) {
    ErrorsResponse errorsResponse = new ErrorsResponse();
    for (int i = 0; i < errors.size(); i++) {
      errorsResponse.add(
          ErrorResponse.builder().code("smart.id.error." + i).message(errors.get(i)).build());
    }
    return new SmartIdException(errorsResponse);
  }
}
