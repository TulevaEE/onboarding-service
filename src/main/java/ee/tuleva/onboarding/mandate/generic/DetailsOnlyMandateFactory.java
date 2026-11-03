package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionDecorator;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContacts;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.details.MandateDetails;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class DetailsOnlyMandateFactory extends MandateFactory<MandateDetails> {

  public DetailsOnlyMandateFactory(
      UserService userService,
      MandateContacts mandateContacts,
      UserConversionService conversionService,
      ConversionDecorator conversionDecorator,
      SecondPillarPaymentRateService secondPillarPaymentRateService) {
    super(
        userService,
        mandateContacts,
        conversionService,
        conversionDecorator,
        secondPillarPaymentRateService);
  }

  @Override
  Mandate createMandate(
      AuthenticatedPerson authenticatedPerson, MandateDto<MandateDetails> mandateCreationDto) {
    return setupMandate(authenticatedPerson, mandateCreationDto);
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == MandateType.PAYMENT_RATE_CHANGE || mandateType == MandateType.REJOIN;
  }
}
