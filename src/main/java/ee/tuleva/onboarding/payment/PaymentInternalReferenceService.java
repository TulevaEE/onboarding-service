package ee.tuleva.onboarding.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalReferenceService {

  private final ObjectMapper mapper;

  public String getPaymentReference(AuthenticatedPerson authenticatedPerson) {
    PaymentReference paymentReference =
        new PaymentReference(authenticatedPerson.getPersonalCode(), UUID.randomUUID());
    try {
      return mapper.writeValueAsString(paymentReference);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
