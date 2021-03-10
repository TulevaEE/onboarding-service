package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CreateMandateCommandWrapper {
  private final CreateMandateCommand createMandateCommand;
  private final User user;
  private final ConversionResponse conversion;
  private final UserPreferences contactDetails;
}
