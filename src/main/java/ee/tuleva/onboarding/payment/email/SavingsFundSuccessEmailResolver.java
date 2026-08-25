package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.user.User;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@NullMarked
@RequiredArgsConstructor
class SavingsFundSuccessEmailResolver {

  private final ParentChildLinkService parentChildLinkService;
  private final PartyResolver partyResolver;

  SavingsFundPaymentEmail resolve(SavingsPaymentCreatedEvent event) {
    PartyId recipient = event.getRecipient();
    return switch (recipient.type()) {
      case LEGAL_ENTITY -> SavingsFundPaymentEmail.companySuccess(nameOf(recipient));
      case PERSON ->
          isRepresentedChild(event.getUser(), recipient)
              ? SavingsFundPaymentEmail.childSuccess(nameOf(recipient))
              : SavingsFundPaymentEmail.personSuccess();
    };
  }

  private boolean isRepresentedChild(User payer, PartyId recipient) {
    return !payer.getPersonalCode().equals(recipient.code())
        && parentChildLinkService.isRepresentation(
            payer.getPersonalCode(), recipient.code(), Set.of(ACTIVE, PENDING_KYC));
  }

  private @Nullable String nameOf(PartyId recipient) {
    return partyResolver.resolve(recipient).map(Party::name).orElse(null);
  }
}
