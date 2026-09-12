package ee.tuleva.onboarding.banking.check.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface PaymentCheckEventRepository extends CrudRepository<PaymentCheckEvent, Long> {

  Optional<PaymentCheckEvent> findByCheckTypeAndExternalKey(
      PaymentCheckType checkType, String externalKey);

  List<PaymentCheckEvent> findBySeverityAndCreatedAtBetween(
      PaymentCheckSeverity severity, Instant from, Instant to);
}
