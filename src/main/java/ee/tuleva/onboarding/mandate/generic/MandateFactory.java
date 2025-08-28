package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class MandateFactory<TDetails extends MandateDetails> {

  private final UserService userService;
  private final EpisService episService;
  private final UserConversionService conversionService;
  private final ConversionDecorator conversionDecorator;
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;

  abstract Mandate createMandate(
      AuthenticatedPerson authenticatedPerson, MandateDto<TDetails> mandateCreationDto);

  abstract boolean supports(MandateType mandateType);

  Mandate setupMandate(
      AuthenticatedPerson authenticatedPerson, MandateDto<TDetails> mandateCreationDto) {
    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();
    ConversionResponse conversion = conversionService.getConversion(user);
    ContactDetails contactDetails = episService.getContactDetails(user);

    Mandate mandate = new Mandate();
    mandate.setUser(user);
    mandate.setAddress(contactDetails.getAddress());
    var paymentRates = secondPillarPaymentRateService.getPaymentRates(authenticatedPerson);
    conversionDecorator.addConversionMetadata(
        mandate.getMetadata(), conversion, contactDetails, authenticatedPerson, paymentRates);

    mandate.setFundTransferExchanges(List.of());
    mandate.setDetails(mandateCreationDto.getDetails());

    return mandate;
  }
}
