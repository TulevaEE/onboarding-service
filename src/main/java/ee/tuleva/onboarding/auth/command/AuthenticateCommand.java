package ee.tuleva.onboarding.auth.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthenticateCommand {
  private String phoneNumber;
  private String personalCode;
  private AuthenticationType type;
}
