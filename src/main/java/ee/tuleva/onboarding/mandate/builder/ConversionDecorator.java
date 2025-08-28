package ee.tuleva.onboarding.mandate.builder;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConversionDecorator {

  public void addConversionMetadata(
      Map<String, Object> metadata,
      ConversionResponse conversion,
      ContactDetails contactDetails,
      AuthenticatedPerson authenticatedPerson,
      PaymentRates paymentRates) {
    metadata.put("isSecondPillarActive", contactDetails.isSecondPillarActive());
    metadata.put("isSecondPillarPartiallyConverted", conversion.isSecondPillarPartiallyConverted());
    metadata.put("isSecondPillarFullyConverted", conversion.isSecondPillarFullyConverted());
    metadata.put("secondPillarWeightedAverageFee", conversion.getSecondPillarWeightedAverageFee());
    metadata.put("secondPillarPaymentRate", paymentRates.getCurrent());

    metadata.put("isThirdPillarActive", contactDetails.isThirdPillarActive());
    metadata.put("isThirdPillarPartiallyConverted", conversion.isThirdPillarPartiallyConverted());
    metadata.put("isThirdPillarFullyConverted", conversion.isThirdPillarFullyConverted());
    metadata.put("thirdPillarWeightedAverageFee", conversion.getThirdPillarWeightedAverageFee());

    metadata.put("authAttributes", authenticatedPerson.getAttributes());
  }
}
