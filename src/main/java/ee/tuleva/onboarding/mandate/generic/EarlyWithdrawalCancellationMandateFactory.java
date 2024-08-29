package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.GenericMandateCreationDto;
import ee.tuleva.onboarding.epis.mandate.details.EarlyWithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class EarlyWithdrawalCancellationMandateFactory
    extends MandateFactory<EarlyWithdrawalCancellationMandateDetails> {

  public EarlyWithdrawalCancellationMandateFactory(
      UserService userService,
      EpisService episService,
      UserConversionService conversionService,
      ConversionDecorator conversionDecorator) {
    super(userService, episService, conversionService, conversionDecorator);
  }

  @Override
  public Mandate createMandate(
      AuthenticatedPerson authenticatedPerson,
      GenericMandateCreationDto<EarlyWithdrawalCancellationMandateDetails> mandateCreationDto) {
    Mandate mandate = this.setupMandate(authenticatedPerson, mandateCreationDto);

    // TODO legacy fields
    mandate.setPillar(2);

    mandate.setDetails(new EarlyWithdrawalCancellationMandateDetails());

    return mandate;
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == MandateType.EARLY_WITHDRAWAL_CANCELLATION;
  }
}
