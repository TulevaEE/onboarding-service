package ee.tuleva.onboarding.auth.command;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthenticateCommand {
  private String phoneNumber;
  @ValidPersonalCode
  private String personalCode;
  private AuthenticationType type;
}
