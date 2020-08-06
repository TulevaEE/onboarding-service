package ee.tuleva.onboarding.auth.mobileid;

import ee.sk.mid.MidHashToSign;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Getter
@RequiredArgsConstructor
public class MobileIDSession implements Serializable {

  private static final long serialVersionUID = -7501351267187058440L;

    private final String sessionId;
    private final String challenge;
    private final MidHashToSign authenticationHash;
    private final String phoneNumber;
    private String firstName;
    private String lastName;
    private String personalCode;

  public String getFullName() {
    return firstName + "\u00A0" + lastName;
  }

  public void updateSessionInfo(String firstName, String lastName, String personalCode) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.personalCode = personalCode;
  }

  @Override
  public String toString() {
    return sessionId
        + ":::"
        + challenge
        + ":::"
        + firstName
        + ":::"
        + lastName
        + ":::"
        + personalCode
        + ":::"
        + phoneNumber
        + ":::"
        + authenticationHash.getHashInBase64();
  }
}
