package ee.tuleva.onboarding.payment.recurring;

import java.time.Instant;
import org.springframework.data.repository.CrudRepository;

interface RecurringPaymentConfirmationRepository
    extends CrudRepository<RecurringPaymentConfirmation, Long> {

  boolean existsByPersonalCodeAndConfirmedAtGreaterThanEqual(String personalCode, Instant since);
}
