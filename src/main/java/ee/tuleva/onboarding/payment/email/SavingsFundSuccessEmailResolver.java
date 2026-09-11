package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;

import ee.tuleva.onboarding.auth.role.CompanyRoles;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.user.User;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
@RequiredArgsConstructor
class SavingsFundSuccessEmailResolver {

  private final ParentChildLinkService parentChildLinkService;
  private final PartyResolver partyResolver;
  private final CompanyRoles companyRoles;

  SavingsFundPaymentEmail resolve(SavingsPaymentCreatedEvent event) {
    PartyId recipient = event.getRecipient();
    return switch (recipient.type()) {
      case LEGAL_ENTITY -> companySuccess(recipient);
      case PERSON -> personOrChildSuccess(event.getUser(), recipient);
    };
  }

  private SavingsFundPaymentEmail companySuccess(PartyId recipient) {
    return companyRoles
        .findCompany(recipient.code())
        .map(company -> SavingsFundPaymentEmail.companySuccess(company.name(), company.id()))
        .orElseGet(SavingsFundPaymentEmail::companySuccess);
  }

  private SavingsFundPaymentEmail personOrChildSuccess(User payer, PartyId recipient) {
    if (payer.getPersonalCode().equals(recipient.code())) {
      return SavingsFundPaymentEmail.personSuccess();
    }
    var linkId = representationOf(payer, recipient);
    if (linkId.isEmpty()) {
      return SavingsFundPaymentEmail.personSuccess();
    }
    return childSuccess(recipient, linkId.get());
  }

  private SavingsFundPaymentEmail childSuccess(PartyId recipient, UUID accountId) {
    return partyResolver
        .resolve(recipient)
        .map(Party::name)
        .map(childName -> SavingsFundPaymentEmail.childSuccess(childName, accountId))
        .orElseGet(() -> SavingsFundPaymentEmail.childSuccess(accountId));
  }

  private Optional<UUID> representationOf(User payer, PartyId recipient) {
    return parentChildLinkService.findRepresentation(
        payer.getPersonalCode(), recipient.code(), Set.of(ACTIVE, PENDING_KYC));
  }
}
