package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.company.CompanyFixture.*;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.TokenService;
import ee.tuleva.onboarding.auth.principal.ActingAs;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.company.UserCompanyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleSwitchServiceTest {

  @Mock private CompanyRepository companyRepository;
  @Mock private UserCompanyRepository userCompanyRepository;
  @Mock private PrincipalService principalService;
  @Mock private TokenService tokenService;

  @InjectMocks private RoleSwitchService roleSwitchService;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();

  @Test
  void switchRoleToCompany() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(userCompanyRepository.existsByUserIdAndCompanyIdAndRelationshipType(
            person.getUserId(), SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(true);
    when(principalService.withActingAs(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    AuthenticationTokens tokens =
        roleSwitchService.switchRole(person, new ActingAs.Company(SAMPLE_REGISTRY_CODE));

    assertThat(tokens.accessToken()).isEqualTo("access");
  }

  @Test
  void switchRoleToSelf() {
    when(principalService.withActingAs(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    AuthenticationTokens tokens =
        roleSwitchService.switchRole(person, new ActingAs.Person(person.getPersonalCode()));

    assertThat(tokens.accessToken()).isEqualTo("access");
  }

  @Test
  void switchRoleToSelfWithWrongCodeThrows() {
    assertThatThrownBy(
            () -> roleSwitchService.switchRole(person, new ActingAs.Person("99999999999")))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void switchToCompanyThrowsWhenCompanyNotFound() {
    when(companyRepository.findByRegistryCode("99999999")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> roleSwitchService.switchRole(person, new ActingAs.Company("99999999")))
        .isInstanceOf(CompanyNotFoundException.class);
  }

  @Test
  void switchToCompanyThrowsWhenNotBoardMember() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(userCompanyRepository.existsByUserIdAndCompanyIdAndRelationshipType(
            person.getUserId(), SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(false);

    assertThatThrownBy(
            () -> roleSwitchService.switchRole(person, new ActingAs.Company(SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void shareholderWithoutBoardMembershipCannotSwitchRole() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(userCompanyRepository.existsByUserIdAndCompanyIdAndRelationshipType(
            person.getUserId(), SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(false);

    assertThatThrownBy(
            () -> roleSwitchService.switchRole(person, new ActingAs.Company(SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void getRolesReturnsSelfAndBoardMemberCompanies() {
    var company = sampleCompany().build();
    var membership = sampleBoardMembership(person.getUserId()).build();
    when(userCompanyRepository.findByUserIdAndRelationshipType(person.getUserId(), BOARD_MEMBER))
        .thenReturn(List.of(membership));
    when(companyRepository.findAllById(List.of(SAMPLE_COMPANY_ID))).thenReturn(List.of(company));

    List<RoleController.Role> result = roleSwitchService.getRoles(person);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().actingAs()).isInstanceOf(ActingAs.Person.class);
    assertThat(result.getFirst().actingAs().code()).isEqualTo(person.getPersonalCode());
    assertThat(result.getFirst().name()).isEqualTo(person.getFullName());
    assertThat(result.getLast().actingAs()).isInstanceOf(ActingAs.Company.class);
    assertThat(result.getLast().actingAs().code()).isEqualTo(SAMPLE_REGISTRY_CODE);
    assertThat(result.getLast().name()).isEqualTo(SAMPLE_COMPANY_NAME);
  }

  @Test
  void getRolesExcludesCompaniesWhereUserIsOnlyShareholder() {
    var company = sampleCompany().registryCode("11111111").build();
    var membership = sampleBoardMembership(person.getUserId()).companyId(company.getId()).build();
    when(userCompanyRepository.findByUserIdAndRelationshipType(person.getUserId(), BOARD_MEMBER))
        .thenReturn(List.of(membership));
    when(companyRepository.findAllById(List.of(company.getId()))).thenReturn(List.of(company));

    List<RoleController.Role> result = roleSwitchService.getRoles(person);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().actingAs()).isInstanceOf(ActingAs.Person.class);
    assertThat(result.getLast().actingAs().code()).isEqualTo("11111111");
  }

  @Test
  void otherUserCannotSwitchToCompanyTheyAreNotLinkedTo() {
    var company = sampleCompany().build();
    var otherUser =
        AuthenticatedPerson.builder()
            .personalCode("39911223344")
            .firstName("Other")
            .lastName("User")
            .userId(999L)
            .build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(userCompanyRepository.existsByUserIdAndCompanyIdAndRelationshipType(
            999L, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(otherUser, new ActingAs.Company(SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }
}
