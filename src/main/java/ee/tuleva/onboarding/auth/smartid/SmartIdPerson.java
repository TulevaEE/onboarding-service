package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationIdentity;
import ee.tuleva.onboarding.auth.principal.Person;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class SmartIdPerson implements Person, Serializable {

  @Serial private static final long serialVersionUID = 9073605679881453649L;
  private final String personalCode;
  private final String firstName;
  private final String lastName;
  private final String country;
  private final String documentNumber;

  public SmartIdPerson(AuthenticationIdentity identity, String documentNumber) {
    this.firstName = identity.getGivenName();
    this.lastName = identity.getSurname();
    this.country = identity.getCountry();
    this.personalCode = identity.getIdentityCode();
    this.documentNumber = documentNumber;
  }
}
