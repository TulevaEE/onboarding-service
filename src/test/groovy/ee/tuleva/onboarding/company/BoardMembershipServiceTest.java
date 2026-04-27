package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.CompanyFixture.SAMPLE_COMPANY_ID;
import static ee.tuleva.onboarding.company.CompanyFixture.SAMPLE_REGISTRY_CODE;
import static ee.tuleva.onboarding.company.CompanyFixture.sampleCompany;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoardMembershipServiceTest {

  private static final String PERSONAL_CODE = "38812121215";

  @Mock private CompanyRepository companyRepository;
  @Mock private CompanyPartyRepository companyPartyRepository;
  @InjectMocks private BoardMembershipService boardMembershipService;

  @Test
  void isBoardMember_returnsTrueWhenLocalCompanyPartyRecordExists() {
    given(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .willReturn(Optional.of(sampleCompany().build()));
    given(
            companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
                PERSONAL_CODE, PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .willReturn(true);

    assertThat(boardMembershipService.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isTrue();
  }

  @Test
  void isBoardMember_returnsFalseWhenNoCompanyPartyRecord() {
    given(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .willReturn(Optional.of(sampleCompany().build()));
    given(
            companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
                PERSONAL_CODE, PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .willReturn(false);

    assertThat(boardMembershipService.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isFalse();
  }

  @Test
  void isBoardMember_returnsFalseWhenCompanyNotFound() {
    given(companyRepository.findByRegistryCode("99999999")).willReturn(Optional.empty());

    assertThat(boardMembershipService.isBoardMember(PERSONAL_CODE, "99999999")).isFalse();
  }
}
