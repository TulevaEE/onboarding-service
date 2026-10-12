package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;

public interface PaymentApplications {

  List<? extends Application<?>> getPaymentApplications(AuthenticatedPerson person);
}
