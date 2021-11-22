package ee.tuleva.onboarding.mandate.builder;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import org.springframework.stereotype.Service;

@Service
public class ConversionDecorator {

  public void addConversionMetadata(
      Mandate mandate, ConversionResponse conversion, ContactDetails contactDetails) {
    mandate.putMetadata("isSecondPillarActive", contactDetails.isSecondPillarActive());
    mandate.putMetadata("isSecondPillarFullyConverted", conversion.isSecondPillarFullyConverted());
    mandate.putMetadata("isThirdPillarActive", contactDetails.isThirdPillarActive());
    mandate.putMetadata("isThirdPillarFullyConverted", conversion.isThirdPillarFullyConverted());
  }
}
