package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SmartIdAuthenticationResult;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@RequiredArgsConstructor
@Slf4j
public class SmartIdSession implements Serializable {

  private static final long serialVersionUID = 6407589354898164171L;

  public final String verificationCode;
  private boolean valid = false;
  private List<String> errors = new ArrayList<>();
  private String givenName;
  private String surName;
  private String identityCode;
  private String country;

  public void setAuthenticationResult(SmartIdAuthenticationResult result) {
    if (result.isValid()) {
      valid = true;
      AuthenticationIdentity identity = result.getAuthenticationIdentity();
      givenName = identity.getGivenName();
      surName = identity.getSurName();
      identityCode = identity.getIdentityCode();
      country = identity.getCountry();
    } else {
      valid = false;
      errors.addAll(result.getErrors());
      log.info("SmartID errors: {}", errors);
    }
  }
}
