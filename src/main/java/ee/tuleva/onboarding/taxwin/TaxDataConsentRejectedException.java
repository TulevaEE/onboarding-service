package ee.tuleva.onboarding.taxwin;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(UNPROCESSABLE_CONTENT)
class TaxDataConsentRejectedException extends RuntimeException {

  TaxDataConsentRejectedException(Long id) {
    super("Consent service rejected the signed tax data consent: id=" + id);
  }
}
