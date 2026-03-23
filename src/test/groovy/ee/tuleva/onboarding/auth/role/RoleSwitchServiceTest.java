package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
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
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.company.UserCompany;
import ee.tuleva.onboarding.company.UserCompanyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  void switchRoleToCompanyDelegatesToSwitchToCompany() {
    var companyId = UUID.randomUUID();
    var company = Company.builder().id(companyId).registryCode("12345678").name("Test OÜ").build();
    when(companyRepository.findByRegistryCode("12345678")).thenReturn(Optional.of(company));
    when(userCompanyRepository.existsByUserIdAndCompanyIdAndRelationshipType(
            person.getUserId(), companyId, BOARD_MEMBER))
        .thenReturn(true);
    when(principalService.withActingAs(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    AuthenticationTokens tokens =
        roleSwitchService.switchRole(person, new ActingAs.Company("12345678"));

    assertThat(tokens.accessToken()).isEqualTo("access");
  }

  @Test
  void switchRoleToSelfDelegatesToSwitchToSelf() {
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
    var companyId = UUID.randomUUID();
    var company = Company.builder().id(companyId).registryCode("12345678").name("Test OÜ").build();
    when(companyRepository.findByRegistryCode("12345678")).thenReturn(Optional.of(company));
    when(userCompanyRepository.existsByUserIdAndCompanyIdAndRelationshipType(
            person.getUserId(), companyId, BOARD_MEMBER))
        .thenReturn(false);

    assertThatThrownBy(() -> roleSwitchService.switchRole(person, new ActingAs.Company("12345678")))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void getRolesReturnsSelfAndBoardMemberCompanies() {
    var companyId = UUID.randomUUID();
    var company = Company.builder().id(companyId).registryCode("12345678").name("Test OÜ").build();
    var userCompany =
        UserCompany.builder()
            .userId(person.getUserId())
            .companyId(companyId)
            .relationshipType(BOARD_MEMBER)
            .build();
    when(userCompanyRepository.findByUserIdAndRelationshipType(person.getUserId(), BOARD_MEMBER))
        .thenReturn(List.of(userCompany));
    when(companyRepository.findAllById(List.of(companyId))).thenReturn(List.of(company));

    List<RoleController.Role> result = roleSwitchService.getRoles(person);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().actingAs()).isInstanceOf(ActingAs.Person.class);
    assertThat(result.getFirst().actingAs().code()).isEqualTo(person.getPersonalCode());
    assertThat(result.getFirst().name()).isEqualTo(person.getFullName());
    assertThat(result.getLast().actingAs()).isInstanceOf(ActingAs.Company.class);
    assertThat(result.getLast().actingAs().code()).isEqualTo("12345678");
    assertThat(result.getLast().name()).isEqualTo("Test OÜ");
  }
}
