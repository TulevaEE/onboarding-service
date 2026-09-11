package ee.tuleva.onboarding.signature;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import org.junit.jupiter.api.Test;

class IdSessionExceptionTest {

  @Test
  void mobileSessionNotFoundCarriesTheExpectedError() {
    var exception = IdSessionException.mobileSessionNotFound();

    assertThat(exception.getErrorsResponse())
        .isEqualTo(
            ErrorsResponse.ofSingleError(
                "mobile.id.session.not.found", "Mobile id session not found"));
  }

  @Test
  void smartIdSessionNotFoundCarriesTheExpectedError() {
    var exception = IdSessionException.smartIdSessionNotFound();

    assertThat(exception.getErrorsResponse())
        .isEqualTo(
            ErrorsResponse.ofSingleError(
                "smart.id.session.not.found", "Smart ID session not found"));
  }

  @Test
  void mobileSignatureSessionNotFoundCarriesTheExpectedError() {
    var exception = IdSessionException.mobileSignatureSessionNotFound();

    assertThat(exception.getErrorsResponse())
        .isEqualTo(
            ErrorsResponse.ofSingleError(
                "mobile.id.signature.session.not.found", "Mobile id signature session not found"));
  }

  @Test
  void smartIdSignatureSessionNotFoundCarriesTheExpectedError() {
    var exception = IdSessionException.smartIdSignatureSessionNotFound();

    assertThat(exception.getErrorsResponse())
        .isEqualTo(
            ErrorsResponse.ofSingleError(
                "smart.id.signature.session.not.found", "Smart ID signature session not found"));
  }

  @Test
  void cardSignatureSessionNotFoundCarriesTheExpectedError() {
    var exception = IdSessionException.cardSignatureSessionNotFound();

    assertThat(exception.getErrorsResponse())
        .isEqualTo(
            ErrorsResponse.ofSingleError(
                "id.card.signature.session.not.found", "No ID card signature session found"));
  }
}
