package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface OutgoingPaymentRepository extends CrudRepository<OutgoingPayment, Long> {
  Optional<OutgoingPayment> findByEndToEndId(String endToEndId);

  List<OutgoingPayment> findByStatusIn(Collection<OutgoingPaymentStatus> statuses);

  List<OutgoingPayment> findByAttemptedAtBetween(Instant from, Instant to);

  List<OutgoingPayment> findByStatusAndAttemptedAtBefore(
      OutgoingPaymentStatus status, Instant before);

  default List<OutgoingPayment> findAwaitingApproval() {
    return findByStatusIn(List.of(ATTEMPTED, SUBMITTED));
  }
}
