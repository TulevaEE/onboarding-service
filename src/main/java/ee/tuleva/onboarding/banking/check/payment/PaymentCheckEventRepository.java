package ee.tuleva.onboarding.banking.check.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentCheckEventRepository extends CrudRepository<PaymentCheckEvent, Long> {
  Optional<PaymentCheckEvent> findByCheckTypeAndExternalKey(
      PaymentCheckType checkType, String externalKey);

  List<PaymentCheckEvent> findBySeverityAndLastSeenAtGreaterThanEqualAndLastSeenAtLessThan(
      PaymentCheckSeverity severity, Instant from, Instant until);

  @Modifying
  @Transactional
  @Query("UPDATE PaymentCheckEvent e SET e.alertFailed = true WHERE e.id = :id")
  void markAlertFailed(Long id);
}
