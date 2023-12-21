package ee.tuleva.onboarding.mandate.payment.rate;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRateService {

  private final MandateService mandateService;
  private final UserService userService;
  private final EpisService episService;
  private final UserConversionService conversionService;
  private final PaymentRateMandateBuilder paymentRateMandateBuilder;

  public Mandate savePaymentRateMandate(
      AuthenticatedPerson authenticatedPerson, BigDecimal paymentRate) {
    User user = userService.getById(authenticatedPerson.getUserId());
    ConversionResponse conversion = conversionService.getConversion(user);
    ContactDetails contactDetails = episService.getContactDetails(user);
    Mandate mandate =
        paymentRateMandateBuilder.build(
            paymentRate, authenticatedPerson, user, conversion, contactDetails);
    return mandateService.save(user, mandate);
  }
}
