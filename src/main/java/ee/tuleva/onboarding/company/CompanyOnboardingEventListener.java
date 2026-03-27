package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.RelationshipType.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.DATA_CHANGED;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CompanyOnboardingEventListener {

  private final CompanyRepository companyRepository;
  private final CompanyPartyRepository companyPartyRepository;

  @EventListener
  @Transactional
  public void onKybCheckPerformed(KybCheckPerformedEvent event) {
    if (noNonDataChangedCheckFailed(event)) {
      var company =
          companyRepository
              .findByRegistryCode(event.getCompany().registryCode().value())
              .orElseGet(() -> createCompany(event));
      replaceParties(company, event);
    }
  }

  private void replaceParties(Company company, KybCheckPerformedEvent event) {
    companyPartyRepository.deleteByCompanyId(company.getId());
    event.getRelatedPersons().stream()
        .flatMap(person -> toCompanyParties(person, company.getId()).stream())
        .forEach(companyPartyRepository::save);
  }

  private List<CompanyParty> toCompanyParties(KybRelatedPerson person, UUID companyId) {
    var parties = new ArrayList<CompanyParty>();
    var code = person.personalCode().value();
    if (person.boardMember()) {
      parties.add(buildParty(code, companyId, BOARD_MEMBER));
    }
    if (person.shareholder()) {
      parties.add(buildParty(code, companyId, SHAREHOLDER));
    }
    if (person.beneficialOwner()) {
      parties.add(buildParty(code, companyId, BENEFICIAL_OWNER));
    }
    return parties;
  }

  private CompanyParty buildParty(String partyCode, UUID companyId, RelationshipType type) {
    return CompanyParty.builder()
        .partyCode(partyCode)
        .partyType(PERSON)
        .companyId(companyId)
        .relationshipType(type)
        .build();
  }

  private boolean noNonDataChangedCheckFailed(KybCheckPerformedEvent event) {
    return event.getChecks().stream()
        .filter(check -> check.type() != DATA_CHANGED)
        .allMatch(KybCheck::success);
  }

  private Company createCompany(KybCheckPerformedEvent event) {
    return companyRepository.save(
        Company.builder()
            .registryCode(event.getCompany().registryCode().value())
            .name(event.getCompany().name())
            .build());
  }
}
