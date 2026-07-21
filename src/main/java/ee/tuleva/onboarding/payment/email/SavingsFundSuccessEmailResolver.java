package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_CHILD;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_PERSON;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsFundSuccessEmailResolver {

  private final ParentChildLinkService parentChildLinkService;
  private final PartyResolver partyResolver;

  record ResolvedEmail(EmailType emailType, String recipientName) {}

  ResolvedEmail resolve(User payer, SavingsPaymentCreatedEvent event) {
    String recipientCode = event.getRecipientPersonalCode();
    PartyId.Type recipientType = event.getRecipientPartyType();

    if (recipientType == LEGAL_ENTITY) {
      return new ResolvedEmail(
          SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY, resolveName(LEGAL_ENTITY, recipientCode));
    }

    if (isForRepresentedChild(payer, recipientCode)) {
      return new ResolvedEmail(
          SAVINGS_FUND_PAYMENT_SUCCESS_CHILD, resolveName(PERSON, recipientCode));
    }

    return new ResolvedEmail(SAVINGS_FUND_PAYMENT_SUCCESS_PERSON, null);
  }

  private boolean isForRepresentedChild(User payer, String recipientCode) {
    return recipientCode != null
        && !recipientCode.equals(payer.getPersonalCode())
        && parentChildLinkService.isActiveRepresentation(payer.getPersonalCode(), recipientCode);
  }

  private String resolveName(PartyId.Type type, String code) {
    if (code == null) {
      return null;
    }
    return partyResolver.resolve(new PartyId(type, code)).map(Party::name).orElse(null);
  }
}
