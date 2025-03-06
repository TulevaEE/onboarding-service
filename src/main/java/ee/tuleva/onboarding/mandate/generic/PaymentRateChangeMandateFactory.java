package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.details.PaymentRateChangeMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class PaymentRateChangeMandateFactory
    extends MandateFactory<PaymentRateChangeMandateDetails> {

  public PaymentRateChangeMandateFactory(
      UserService userService,
      EpisService episService,
      UserConversionService conversionService,
      ConversionDecorator conversionDecorator) {
    super(userService, episService, conversionService, conversionDecorator);
  }

  @Override
  Mandate createMandate(
      AuthenticatedPerson authenticatedPerson,
      MandateDto<PaymentRateChangeMandateDetails> mandateCreationDto) {
    Mandate mandate = this.setupMandate(authenticatedPerson, mandateCreationDto);
    PaymentRateChangeMandateDetails details = mandateCreationDto.getDetails();

    mandate.setPillar(2);
    // TODO legacy field
    mandate.setPaymentRate(details.getPaymentRate().getNumericValue());

    return mandate;
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == MandateType.PAYMENT_RATE_CHANGE;
  }
}
