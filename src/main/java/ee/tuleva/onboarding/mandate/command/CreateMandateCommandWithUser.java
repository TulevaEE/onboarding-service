package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CreateMandateCommandWithUser {
  private final CreateMandateCommand createMandateCommand;
  private final User user;
}
