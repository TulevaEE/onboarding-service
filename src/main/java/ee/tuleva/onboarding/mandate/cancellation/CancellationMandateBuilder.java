package ee.tuleva.onboarding.mandate.cancellation;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CancellationMandateBuilder {

  private final ConversionDecorator conversionDecorator;

  public Mandate build(
      ApplicationType applicationTypeToCancel,
      User user,
      ConversionResponse conversion,
      UserPreferences contactDetails) {

    Mandate mandate = new Mandate();
    mandate.setUser(user);
    mandate.setPillar(2); // Can only cancel 2nd pillar applications for now
    mandate.setAddress(contactDetails.getAddress());
    mandate.putMetadata("applicationTypeToCancel", applicationTypeToCancel);
    conversionDecorator.addConversionMetadata(mandate, conversion, contactDetails);

    return mandate;
  }
}
