package ee.tuleva.onboarding.auth.exception;

import lombok.Getter;

public class AuthenticationException extends RuntimeException {
  public enum Code {
    INVALID_INPUT("Sisendparameetrid mittekorrektsel kujul"),
    USER_CERTIFICATE_MISSING("Kasutaja sertifikaat puudub"),
    UNABLE_TO_TEST_USER_CERTIFICATE("Kasutaja sertifikaadi kehtivust ei ole võimalik kontrollida"),
    REVOKED("Kasutaja sertifikaat ei kehti (OCSP vastus REVOKED)."),
    UNKNOWN("Kasutaja sertifikaat on teadmata staatuses (OCSP vastus UNKNOWN)."),
    EXPIRED("Kasutaja sertifikaat on aegunud"),
    UNAUTHORIZED(
        "Ligipääs antud meetodile antud parameetritega piiratud (Ligipääs ei ole teenuse pakkuja juures registreeritud)"),
    INTERNAL_ERROR("Teenuse tehniline viga");

    private final String descriptionInEstonian;

    Code(String descriptionInEstonian) {
      this.descriptionInEstonian = descriptionInEstonian;
    }
  }

  @Getter private Code code;

  public AuthenticationException(Code code, String details, Throwable throwable) {
    super(code + ": " + details, throwable);
    this.code = code;
  }

  public AuthenticationException(Code code, String details) {
    super(code + ": " + details);
    this.code = code;
  }

  public AuthenticationException(Code code) {
    super(code.toString());
    this.code = code;
  }
}
