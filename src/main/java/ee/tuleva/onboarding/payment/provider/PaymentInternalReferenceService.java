package ee.tuleva.onboarding.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalReferenceService {

  private final ObjectMapper mapper;

  private final LocaleService localeService;

  @SneakyThrows
  public String getPaymentReference(Person person, PaymentData paymentData, String description) {
    PaymentReference paymentReference =
        new PaymentReference(
            person.getPersonalCode(),
            paymentData.getRecipientPersonalCode(),
            UUID.randomUUID(),
            paymentData.getType(),
            localeService.getCurrentLocale(),
            description);
    return mapper.writeValueAsString(paymentReference);
  }
}
