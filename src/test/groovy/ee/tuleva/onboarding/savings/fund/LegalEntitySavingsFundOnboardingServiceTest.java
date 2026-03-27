package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.company.CompanyFixture.*;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.company.CompanyPartyRepository;
import ee.tuleva.onboarding.company.CompanyRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegalEntitySavingsFundOnboardingServiceTest {

  @Mock private CompanyRepository companyRepository;
  @Mock private CompanyPartyRepository companyPartyRepository;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @InjectMocks private LegalEntitySavingsFundOnboardingService service;

  private static final String PERSONAL_CODE = "38812121215";

  @Test
  void getOnboardingStatus_returnsStatusForBoardMember() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
            PERSONAL_CODE, PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(true);
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(COMPLETED));

    assertThat(service.getOnboardingStatus(PERSONAL_CODE, SAMPLE_REGISTRY_CODE))
        .hasValue(COMPLETED);
  }

  @Test
  void getOnboardingStatus_returnsEmptyWhenNoStatus() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
            PERSONAL_CODE, PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(true);
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.empty());

    assertThat(service.getOnboardingStatus(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isEmpty();
  }

  @Test
  void getOnboardingStatus_returnsEmptyWhenNotBoardMember() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
            PERSONAL_CODE, PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(false);

    assertThat(service.getOnboardingStatus(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isEmpty();
  }

  @Test
  void getOnboardingStatus_returnsEmptyWhenCompanyNotFound() {
    when(companyRepository.findByRegistryCode("99999999")).thenReturn(Optional.empty());

    assertThat(service.getOnboardingStatus(PERSONAL_CODE, "99999999")).isEmpty();
  }

  @Test
  void isOnboardingCompleted_returnsTrueForCompletedBoardMember() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
            PERSONAL_CODE, PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(true);
    when(savingsFundOnboardingRepository.isOnboardingCompleted(SAMPLE_REGISTRY_CODE))
        .thenReturn(true);

    assertThat(service.isOnboardingCompleted(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isTrue();
  }
}
