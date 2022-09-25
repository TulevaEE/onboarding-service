package ee.tuleva.onboarding.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.principal.Person;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalReferenceService {

  private final ObjectMapper mapper;

  @SneakyThrows
  public String getPaymentReference(Person person) {
    PaymentReference paymentReference =
        new PaymentReference(person.getPersonalCode(), UUID.randomUUID());
    return mapper.writeValueAsString(paymentReference);
  }
}
