package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.mandate.MandateType.*;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionDecorator;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContacts;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.details.EarlyWithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class EarlyWithdrawalCancellationMandateFactory
    extends MandateFactory<EarlyWithdrawalCancellationMandateDetails> {

  public EarlyWithdrawalCancellationMandateFactory(
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
      MandateDto<EarlyWithdrawalCancellationMandateDetails> mandateCreationDto) {
    Mandate mandate = this.setupMandate(authenticatedPerson, mandateCreationDto);

    // TODO legacy fields
    mandate.setPillar(2);

    return mandate;
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == EARLY_WITHDRAWAL_CANCELLATION;
  }
}
