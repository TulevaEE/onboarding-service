package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class ConversionDecorator {

  public void addConversionMetadata(
      Map<String, @Nullable Object> metadata,
      ConversionResponse conversion,
      boolean secondPillarActive,
      boolean thirdPillarActive,
      AuthenticatedPerson authenticatedPerson,
      PaymentRates paymentRates) {
    metadata.put("isSecondPillarActive", secondPillarActive);
    metadata.put("isSecondPillarPartiallyConverted", conversion.isSecondPillarPartiallyConverted());
    metadata.put("isSecondPillarFullyConverted", conversion.isSecondPillarFullyConverted());
    metadata.put("secondPillarWeightedAverageFee", conversion.getSecondPillarWeightedAverageFee());
    metadata.put("secondPillarPaymentRate", paymentRates.getCurrent());

    metadata.put("isThirdPillarActive", thirdPillarActive);
    metadata.put("isThirdPillarPartiallyConverted", conversion.isThirdPillarPartiallyConverted());
    metadata.put("isThirdPillarFullyConverted", conversion.isThirdPillarFullyConverted());
    metadata.put("thirdPillarWeightedAverageFee", conversion.getThirdPillarWeightedAverageFee());

    metadata.put("authAttributes", authenticatedPerson.getAttributes());
  }
}
