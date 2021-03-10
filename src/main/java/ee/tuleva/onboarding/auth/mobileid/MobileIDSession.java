package ee.tuleva.onboarding.auth.mobileid;

import ee.sk.mid.MidHashToSign;
import ee.tuleva.onboarding.auth.principal.Person;
import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MobileIDSession implements Person, Serializable {

  private static final long serialVersionUID = -7501351267187058440L;

  private final String sessionId;
  private final String challenge;
  private final MidHashToSign authenticationHash;
  private final String phoneNumber;
  private String firstName;
  private String lastName;
  private String personalCode;

  public void updateSessionInfo(String firstName, String lastName, String personalCode) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.personalCode = personalCode;
  }
}
