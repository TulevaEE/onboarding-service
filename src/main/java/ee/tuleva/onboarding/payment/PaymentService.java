package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PaymentService {

  private final PaymentRepository paymentRepository;

  public List<Payment> getPayments(Person person, PaymentStatus status) {
    return paymentRepository.findAllByUserPersonalCodeAndStatus(person.getPersonalCode(), status);
  }
}
