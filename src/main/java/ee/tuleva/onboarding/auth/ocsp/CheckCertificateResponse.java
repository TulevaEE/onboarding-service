package ee.tuleva.onboarding.auth.ocsp;

import ee.tuleva.onboarding.auth.principal.Person;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Getter
@RequiredArgsConstructor
public class CheckCertificateResponse implements Serializable, Person {

  private static final long serialVersionUID = 333351175769353073L;

  private final String firstName;
  private final String lastName;
  private final String personalCode;

  public String getFullName() {
    return firstName + "\u00A0" + lastName;
  }

  @Override
  public String toString() {
    return firstName + ":::" + lastName + ":::" + personalCode;
  }

  public static CheckCertificateResponse fromString(String serializedMobileIDSession) {
    String[] tokens = serializedMobileIDSession.split(":::");
    return new CheckCertificateResponse(tokens[0], tokens[1], tokens[2]);
  }
}