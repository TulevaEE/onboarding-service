package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.Data;

@Data
public class SmartIdSession implements Serializable {

  @Serial private static final long serialVersionUID = 7445120994281540801L;

  private final String verificationCode;
  private final String personalCode;
  private final AuthenticationHash authenticationHash;
  private final Instant createdAt;
  private SmartIdPerson person;
  private String errorCode;
  private String errorMessage;
}
