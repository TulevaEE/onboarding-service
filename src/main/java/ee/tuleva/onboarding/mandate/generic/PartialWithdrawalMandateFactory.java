package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class PartialWithdrawalMandateFactory
    extends MandateFactory<PartialWithdrawalMandateDetails> {

  public PartialWithdrawalMandateFactory(
      UserService userService,
      EpisService episService,
      UserConversionService conversionService,
      ConversionDecorator conversionDecorator,
      SecondPillarPaymentRateService secondPillarPaymentRateService) {
    super(
        userService,
        episService,
        conversionService,
        conversionDecorator,
        secondPillarPaymentRateService);
  }

  @Override
  public Mandate createMandate(
      AuthenticatedPerson authenticatedPerson,
      MandateDto<PartialWithdrawalMandateDetails> mandateCreationDto) {
    Mandate mandate = this.setupMandate(authenticatedPerson, mandateCreationDto);
    PartialWithdrawalMandateDetails details = mandateCreationDto.getDetails();

    // TODO legacy field
    mandate.setPillar(details.getPillar().toInt());

    return mandate;
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == MandateType.PARTIAL_WITHDRAWAL;
  }
}
