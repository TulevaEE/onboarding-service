package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.PartyType.PERSON;
import static ee.tuleva.onboarding.company.RelationshipType.*;

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
    if (!allChecksPassed(event)) {
      return;
    }

    var company =
        companyRepository
            .findByRegistryCode(event.getCompany().registryCode().value())
            .orElseGet(() -> createCompany(event));

    // TODO: update existing party records when board members change on re-screening
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

  private boolean allChecksPassed(KybCheckPerformedEvent event) {
    return event.getChecks().stream().allMatch(KybCheck::success);
  }

  private Company createCompany(KybCheckPerformedEvent event) {
    return companyRepository.save(
        Company.builder()
            .registryCode(event.getCompany().registryCode().value())
            .name(event.getCompany().name())
            .build());
  }
}
