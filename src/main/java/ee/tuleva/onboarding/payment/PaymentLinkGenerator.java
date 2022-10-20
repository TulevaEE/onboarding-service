package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.Person;

public interface PaymentLinkGenerator {

  PaymentLink getPaymentLink(PaymentData paymentData, Person person);
}
