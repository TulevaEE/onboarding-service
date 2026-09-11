package ee.tuleva.onboarding.notification.email.firstpayment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FirstPaymentAudience {

  Optional<LocalDate> oldestOwnPaymentDate();

  List<FirstThirdPillarPayment> fetchUnemailedFirstPayments(
      LocalDate windowStart, LocalDate adultBirthDateCutoff);
}
