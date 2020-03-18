package ee.tuleva.onboarding.auth.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthenticateCommand {
  @Deprecated String phoneNumber;
  String value;
  AuthenticationType type;
}
