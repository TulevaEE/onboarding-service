package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

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
public class SavingsFundRecipientResolver {

  private final ParentChildLinkService parentChildLinkService;
  private final PartyResolver partyResolver;

  public enum RecipientType {
    PERSON,
    CHILD,
    COMPANY;

    String mergeValue() {
      return name().toLowerCase();
    }
  }

  public record Recipient(RecipientType type, String name) {}

  public Recipient resolve(User payer, SavingsPaymentCreatedEvent event) {
    String recipientCode = event.getRecipientPersonalCode();
    PartyId.Type recipientPartyType = event.getRecipientPartyType();

    if (recipientPartyType == LEGAL_ENTITY) {
      return new Recipient(RecipientType.COMPANY, resolveName(LEGAL_ENTITY, recipientCode));
    }
    if (isForRepresentedChild(payer, recipientCode)) {
      return new Recipient(RecipientType.CHILD, resolveName(PERSON, recipientCode));
    }
    return new Recipient(RecipientType.PERSON, null);
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
