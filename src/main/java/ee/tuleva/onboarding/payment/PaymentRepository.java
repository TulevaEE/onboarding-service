package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.payment.PaymentData.PaymentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface PaymentRepository extends CrudRepository<Payment, Long> {

  Optional<Payment> findByInternalReference(UUID internalReference);

  List<Payment> findAllByRecipientPersonalCodeAndPaymentTypeNot(
      String personalCode, PaymentType paymentType);
}
