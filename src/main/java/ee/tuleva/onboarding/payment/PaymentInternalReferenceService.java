package ee.tuleva.onboarding.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.principal.Person;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalReferenceService {

  private final ObjectMapper mapper;

  public String getPaymentReference(Person person) {
    PaymentReference paymentReference =
        new PaymentReference(person.getPersonalCode(), UUID.randomUUID());
    try {
      return mapper.writeValueAsString(paymentReference);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
