package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;

import ee.tuleva.onboarding.auth.role.CompanyRoles;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
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
class SavingsFundAccountHolderResolver {

  private final ParentChildLinkService parentChildLinkService;
  private final PartyResolver partyResolver;
  private final CompanyRoles companyRoles;

  SavingsFundAccountHolder resolve(User payer, PartyId recipient) {
    return switch (recipient.type()) {
      case LEGAL_ENTITY -> company(recipient);
      case PERSON -> personOrChild(payer, recipient);
    };
  }

  SavingsFundAccountHolder resolveRetryAccount(User payer, PartyId recipient) {
    if (recipient.type() == LEGAL_ENTITY
        && !companyRoles.isBoardMember(payer.getPersonalCode(), recipient.code())) {
      return SavingsFundAccountHolder.person();
    }
    return resolve(payer, recipient);
  }

  private SavingsFundAccountHolder company(PartyId recipient) {
    return companyRoles
        .findCompany(recipient.code())
        .map(company -> SavingsFundAccountHolder.company(company.name(), company.id()))
        .orElseGet(SavingsFundAccountHolder::companyNotOnFileYet);
  }

  private SavingsFundAccountHolder personOrChild(User payer, PartyId recipient) {
    if (payer.getPersonalCode().equals(recipient.code())) {
      return SavingsFundAccountHolder.person();
    }
    return representationOf(payer, recipient)
        .map(accountId -> child(recipient, accountId))
        .orElseGet(SavingsFundAccountHolder::person);
  }

  private SavingsFundAccountHolder child(PartyId recipient, UUID accountId) {
    return partyResolver
        .resolve(recipient)
        .map(Party::name)
        .map(childName -> SavingsFundAccountHolder.child(childName, accountId))
        .orElseGet(() -> SavingsFundAccountHolder.child(accountId));
  }

  private Optional<UUID> representationOf(User payer, PartyId recipient) {
    return parentChildLinkService.findRepresentation(
        payer.getPersonalCode(), recipient.code(), Set.of(ACTIVE, PENDING_KYC));
  }
}
