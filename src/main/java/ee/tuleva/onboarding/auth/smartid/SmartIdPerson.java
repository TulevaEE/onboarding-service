package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.FlowType;
import ee.tuleva.onboarding.auth.principal.Person;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class SmartIdPerson implements Person, Serializable {

  @Serial private static final long serialVersionUID = 4316207155950617832L;
  private final String personalCode;
  private final String firstName;
  private final String lastName;
  private final String country;
  private final String documentNumber;
  private final FlowType flow;

  public SmartIdPerson(AuthenticationIdentity identity, String documentNumber, FlowType flow) {
    this.firstName = identity.getGivenName();
    this.lastName = identity.getSurname();
    this.country = identity.getCountry();
    this.personalCode = identity.getIdentityCode();
    this.documentNumber = documentNumber;
    this.flow = flow;
  }
}
