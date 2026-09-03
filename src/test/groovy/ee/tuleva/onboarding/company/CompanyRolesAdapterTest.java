package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.CompanyFixture.SAMPLE_COMPANY_ID;
import static ee.tuleva.onboarding.company.CompanyFixture.SAMPLE_COMPANY_NAME;
import static ee.tuleva.onboarding.company.CompanyFixture.SAMPLE_REGISTRY_CODE;
import static ee.tuleva.onboarding.company.CompanyFixture.sampleBoardMembership;
import static ee.tuleva.onboarding.company.CompanyFixture.sampleCompany;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.role.CompanyRoles.CompanyRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyRolesAdapterTest {

  private static final String PERSONAL_CODE = "38812121215";

  @Mock private CompanyRepository companyRepository;
  @Mock private CompanyPartyRepository companyPartyRepository;
  @InjectMocks private CompanyRolesAdapter companyRolesAdapter;

  @Test
  void boardMemberCompaniesReturnsCompaniesForBoardMemberships() {
    var membership = sampleBoardMembership(PERSONAL_CODE).build();
    given(
            companyPartyRepository.findByPartyCodeAndPartyTypeAndRelationshipType(
                PERSONAL_CODE, PERSON, BOARD_MEMBER))
        .willReturn(List.of(membership));
    given(companyRepository.findAllById(List.of(SAMPLE_COMPANY_ID)))
        .willReturn(List.of(sampleCompany().build()));

    assertThat(companyRolesAdapter.boardMemberCompanies(PERSONAL_CODE))
        .containsExactly(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME));
  }

  @Test
  void boardMemberCompaniesIsEmptyWhenNoMemberships() {
    given(
            companyPartyRepository.findByPartyCodeAndPartyTypeAndRelationshipType(
                PERSONAL_CODE, PERSON, BOARD_MEMBER))
        .willReturn(List.of());

    assertThat(companyRolesAdapter.boardMemberCompanies(PERSONAL_CODE)).isEmpty();
  }

  @Test
  void companyReturnsCompanyWhenFound() {
    given(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .willReturn(Optional.of(sampleCompany().build()));

    assertThat(companyRolesAdapter.company(SAMPLE_REGISTRY_CODE))
        .isEqualTo(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME));
  }

  @Test
  void companyThrowsWhenNotFound() {
    given(companyRepository.findByRegistryCode("99999999")).willReturn(Optional.empty());

    assertThatThrownBy(() -> companyRolesAdapter.company("99999999"))
        .isInstanceOf(CompanyNotFoundException.class);
  }

  @Test
  void isBoardMemberReturnsTrueWhenLocalCompanyPartyRecordExists() {
    given(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .willReturn(Optional.of(sampleCompany().build()));
    given(
            companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
                PERSONAL_CODE, PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .willReturn(true);

    assertThat(companyRolesAdapter.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isTrue();
  }

  @Test
  void isBoardMemberReturnsFalseWhenNotABoardMember() {
    given(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .willReturn(Optional.of(sampleCompany().build()));
    given(
            companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
                PERSONAL_CODE, PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .willReturn(false);

    assertThat(companyRolesAdapter.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isFalse();
  }

  @Test
  void isBoardMemberReturnsFalseWhenCompanyNotFound() {
    given(companyRepository.findByRegistryCode("99999999")).willReturn(Optional.empty());

    assertThat(companyRolesAdapter.isBoardMember(PERSONAL_CODE, "99999999")).isFalse();
  }
}
