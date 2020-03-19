package ee.tuleva.onboarding.auth.ocsp;

import lombok.Getter;

public enum OCSPResponseType {
  GOOD(0),
  REVOKED(1),
  EXPIRED(2),
  UNKNOWN(3);
  @Getter private final int status;

  OCSPResponseType(int status) {
    this.status = status;
  }
}
