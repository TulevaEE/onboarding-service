package ee.tuleva.onboarding.auth.ocsp;

import ee.tuleva.onboarding.auth.principal.Person;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString
public class CheckCertificateResponse implements Serializable, Person {

  @Serial private static final long serialVersionUID = 333351175769353073L;

  private final String firstName;
  private final String lastName;
  private final String personalCode;
}
