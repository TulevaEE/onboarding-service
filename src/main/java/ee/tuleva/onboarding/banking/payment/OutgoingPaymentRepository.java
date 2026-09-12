package ee.tuleva.onboarding.banking.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface OutgoingPaymentRepository extends CrudRepository<OutgoingPayment, Long> {

  Optional<OutgoingPayment> findByEndToEndId(String endToEndId);

  List<OutgoingPayment> findByStatus(OutgoingPaymentStatus status);

  List<OutgoingPayment> findByAttemptedAtBetween(Instant from, Instant to);

  List<OutgoingPayment> findByStatusAndAttemptedAtBefore(
      OutgoingPaymentStatus status, Instant before);
}
