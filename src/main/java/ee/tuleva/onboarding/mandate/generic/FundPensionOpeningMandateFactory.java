package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionDecorator;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContacts;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.details.FundPensionOpeningMandateDetails;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class FundPensionOpeningMandateFactory
    extends MandateFactory<FundPensionOpeningMandateDetails> {

  public FundPensionOpeningMandateFactory(
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
  public Mandate createMandate(
      AuthenticatedPerson authenticatedPerson,
      MandateDto<FundPensionOpeningMandateDetails> mandateCreationDto) {
    Mandate mandate = this.setupMandate(authenticatedPerson, mandateCreationDto);

    FundPensionOpeningMandateDetails details = mandateCreationDto.getDetails();

    // TODO legacy field
    mandate.setPillar(details.getPillar().toInt());

    return mandate;
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == MandateType.FUND_PENSION_OPENING;
  }
}
