package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class SmartIdSession implements Serializable {

  @Serial private static final long serialVersionUID = 6326478770346040900L;

  private final String verificationCode;
  private final String personalCode;
  private final AuthenticationHash authenticationHash;
}
