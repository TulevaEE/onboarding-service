package ee.tuleva.onboarding.payment.provider;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.personalcode.PersonalCodeValidator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class PaymentInternalReferenceService {

  private static final PersonalCodeValidator personalCodeValidator = new PersonalCodeValidator();

  private final JsonMapper mapper;

  private final LocaleService localeService;

  @SneakyThrows
  // TODO: should take Party instead of Person so we wouldn't need to infer the PartyType
  public String getPaymentReference(Person person, PaymentData paymentData, String description) {
    PaymentReference paymentReference =
        new PaymentReference(
            person.getPersonalCode(),
            paymentData.getRecipientPersonalCode(),
            UUID.randomUUID(),
            paymentData.getType(),
            localeService.getCurrentLocale(),
            description,
            inferPartyType(paymentData.getRecipientPersonalCode()));
    return mapper.writeValueAsString(paymentReference);
  }

  static PartyId.Type inferPartyType(String code) {
    return personalCodeValidator.isValid(code) ? PartyId.Type.PERSON : PartyId.Type.LEGAL_ENTITY;
  }
}
