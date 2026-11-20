package ee.tuleva.onboarding.taxwin;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(SERVICE_UNAVAILABLE)
class TaxHeadroomUnavailableException extends RuntimeException {

  TaxHeadroomUnavailableException(Long userId) {
    super("Tax data could not be refreshed and no recent data is stored: userId=" + userId);
  }
}
