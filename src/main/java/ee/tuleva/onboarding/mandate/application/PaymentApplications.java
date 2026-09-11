package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.List;

public interface PaymentApplications {

  List<? extends Application<?>> getPaymentApplications(Person person);
}
