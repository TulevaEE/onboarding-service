package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_ACTIVE;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.kybPerson;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.kyb.CompanyDto;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import ee.tuleva.onboarding.kyb.LegalForm;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class CompanyOnboardingEventListenerIntegrationTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final String PERSONAL_CODE = "38501010002";

  @Autowired private CompanyRepository companyRepository;
  @Autowired private CompanyPartyRepository companyPartyRepository;

  private CompanyOnboardingEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new CompanyOnboardingEventListener(companyRepository, companyPartyRepository);
  }

  @Test
  void reOnboardingSameCompanyReplacesPartiesWithoutViolatingUniqueConstraint() {
    onboard();
    companyPartyRepository.flush();

    onboard();
    companyPartyRepository.flush();

    var company = companyRepository.findByRegistryCode(REGISTRY_CODE).orElseThrow();
    var parties =
        companyPartyRepository.findByPartyCodeAndPartyTypeAndRelationshipType(
            PERSONAL_CODE, PERSON, BOARD_MEMBER);
    assertThat(parties)
        .extracting(
            CompanyParty::getCompanyId,
            CompanyParty::getPartyCode,
            CompanyParty::getPartyType,
            CompanyParty::getRelationshipType)
        .containsExactly(tuple(company.getId(), PERSONAL_CODE, PERSON, BOARD_MEMBER));
  }

  private void onboard() {
    var boardMember =
        kybPerson()
            .personalCode(new PersonalCode(PERSONAL_CODE))
            .boardMember(true)
            .kycStatus(COMPLETED)
            .build();
    listener.onKybCheckPerformed(
        new KybCheckPerformedEvent(
            this,
            new CompanyDto(new RegistryCode(REGISTRY_CODE), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode(PERSONAL_CODE),
            List.of(boardMember),
            List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of()))));
  }
}
