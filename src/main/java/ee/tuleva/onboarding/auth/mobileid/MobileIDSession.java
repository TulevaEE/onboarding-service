package ee.tuleva.onboarding.auth.mobileid;

import ee.sk.mid.MidHashToSign;
import java.io.Serializable;
import lombok.Getter;

@Getter
public class MobileIDSession implements Serializable {
  private static final long serialVersionUID = -7501351267187058440L;

  private String firstName;
  private String lastName;
  private String personalCode;
  private String challenge;
  private String sessionId;
  private String phoneNumber;
  private MidHashToSign authenticationHash;

  public MobileIDSession(
      String sessionId, String challenge, MidHashToSign authenticationHash, String phoneNumber) {
    this.challenge = challenge;
    this.sessionId = sessionId;
    this.phoneNumber = phoneNumber;
    this.authenticationHash = authenticationHash;
  }

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
