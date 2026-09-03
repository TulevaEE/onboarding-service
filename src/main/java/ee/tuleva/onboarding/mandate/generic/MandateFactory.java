package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionDecorator;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateContacts;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.details.MandateDetails;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class MandateFactory<TDetails extends MandateDetails> {

  private final UserService userService;
  private final MandateContacts mandateContacts;
  private final UserConversionService conversionService;
  private final ConversionDecorator conversionDecorator;
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;

  abstract Mandate createMandate(
      AuthenticatedPerson authenticatedPerson, MandateDto<TDetails> mandateCreationDto);

  abstract boolean supports(MandateType mandateType);

  Mandate setupMandate(
      AuthenticatedPerson authenticatedPerson, MandateDto<TDetails> mandateCreationDto) {
    User user = userService.getById(authenticatedPerson.getUserIdOrThrow()).orElseThrow();
    ConversionResponse conversion = conversionService.getConversion(user);
    MandateContactDetails contactDetails = mandateContacts.getContactDetails(user);

    Mandate mandate = new Mandate();
    mandate.setUser(user);
    mandate.setAddress(contactDetails.address());
    var paymentRates = secondPillarPaymentRateService.getPaymentRates(authenticatedPerson);
    conversionDecorator.addConversionMetadata(
        mandate.getMetadata(),
        conversion,
        contactDetails.secondPillarActive(),
        contactDetails.thirdPillarActive(),
        authenticatedPerson,
        paymentRates);

    mandate.setFundTransferExchanges(List.of());
    mandate.setDetails(mandateCreationDto.getDetails());

    return mandate;
  }
}
