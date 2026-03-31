package ee.tuleva.onboarding.paymentrate;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecondPillarPaymentRateService {

  private static final int DEFAULT_SECOND_PILLAR_PAYMENT_RATE = 2;
  private final ContactDetailsService contactDetailsService;

  public PaymentRates getPaymentRates(Person person) {
    ContactDetails contactDetails = contactDetailsService.getContactDetails(person);
    ContactDetails.PaymentRates rates = contactDetails.getSecondPillarPaymentRates();

    Integer current = rates != null ? rates.getCurrent() : null;
    Integer pending = rates != null ? rates.getPending() : null;

    return new PaymentRates(
        current != null ? current : DEFAULT_SECOND_PILLAR_PAYMENT_RATE, pending);
  }
}
