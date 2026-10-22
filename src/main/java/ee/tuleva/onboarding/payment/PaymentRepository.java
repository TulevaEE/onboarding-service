package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.payment.PaymentData.PaymentType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface PaymentRepository extends CrudRepository<Payment, Long> {

  Optional<Payment> findByInternalReference(UUID internalReference);

  List<Payment> findAllByRecipientPersonalCodeAndPaymentTypeNot(
      String personalCode, PaymentType paymentType);

  List<Payment> findAllByRecipientPersonalCodeAndPaymentTypeInAndCreatedTimeGreaterThanEqual(
      String personalCode, Collection<PaymentType> paymentTypes, Instant since);
}
