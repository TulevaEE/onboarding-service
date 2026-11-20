package ee.tuleva.onboarding.taxwin;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(SERVICE_UNAVAILABLE)
public class ConsentServiceUnavailableException extends RuntimeException {

  public ConsentServiceUnavailableException(String message) {
    super(message);
  }

  public ConsentServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
