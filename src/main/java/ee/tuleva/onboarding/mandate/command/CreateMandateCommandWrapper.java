package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CreateMandateCommandWrapper {
  private final CreateMandateCommand createMandateCommand;
  private final AuthenticatedPerson authenticatedPerson;
  private final User user;
  private final ConversionResponse conversion;
  private final MandateContactDetails contactDetails;
}
