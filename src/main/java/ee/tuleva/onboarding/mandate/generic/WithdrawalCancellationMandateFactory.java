package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.GenericMandateCreationDto;
import ee.tuleva.onboarding.epis.mandate.details.WithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class WithdrawalCancellationMandateFactory
    extends MandateFactory<WithdrawalCancellationMandateDetails> {

  public WithdrawalCancellationMandateFactory(
      UserService userService,
      EpisService episService,
      UserConversionService conversionService,
      ConversionDecorator conversionDecorator) {
    super(userService, episService, conversionService, conversionDecorator);
  }

  @Override
  public Mandate createMandate(
      AuthenticatedPerson authenticatedPerson,
      GenericMandateCreationDto<WithdrawalCancellationMandateDetails> mandateCreationDto) {
    Mandate mandate = this.setupMandate(authenticatedPerson, mandateCreationDto);

    mandate.setPillar(2);
    mandate.setMandateType(MandateType.WITHDRAWAL_CANCELLATION);

    return mandate;
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == MandateType.WITHDRAWAL_CANCELLATION;
  }
}
