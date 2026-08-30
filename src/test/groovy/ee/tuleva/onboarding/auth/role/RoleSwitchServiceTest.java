package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.role.CompanyRoles.CompanyRole;
import static ee.tuleva.onboarding.auth.role.RoleType.*;
import static ee.tuleva.onboarding.company.CompanyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.TokenService;
import ee.tuleva.onboarding.auth.event.RoleSwitchedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.principal.PrincipalUsers;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RoleSwitchServiceTest {

  @Mock private CompanyRoles companyRoles;
  @Mock private PrincipalService principalService;
  @Mock private TokenService tokenService;
  @Mock private ChildRepresentations childRepresentations;
  @Mock private PrincipalUsers principalUsers;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @InjectMocks private RoleSwitchService roleSwitchService;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();

  private static final String CHILD_CODE = "61506150006";
  private static final String CHILD_NAME = "Mari Maasikas";

  @Test
  void switchRoleToCompany() {
    when(companyRoles.company(SAMPLE_REGISTRY_CODE))
        .thenReturn(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME));
    when(companyRoles.isBoardMember(person.getPersonalCode(), SAMPLE_REGISTRY_CODE))
        .thenReturn(true);
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    AuthenticationTokens tokens =
        roleSwitchService.switchRole(
            person, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE));

    assertThat(tokens.accessToken()).isEqualTo("access");
  }

  @Test
  void switchToCompanyPublishesAuditEvent() {
    when(companyRoles.company(SAMPLE_REGISTRY_CODE))
        .thenReturn(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME));
    when(companyRoles.isBoardMember(person.getPersonalCode(), SAMPLE_REGISTRY_CODE))
        .thenReturn(true);
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    roleSwitchService.switchRole(person, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE));

    verify(applicationEventPublisher).publishEvent(new RoleSwitchedEvent(person, person));
  }

  @Test
  void switchToCompanyPublishesNoAuditEventWhenTokenGenerationFails() {
    when(companyRoles.company(SAMPLE_REGISTRY_CODE))
        .thenReturn(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME));
    when(companyRoles.isBoardMember(person.getPersonalCode(), SAMPLE_REGISTRY_CODE))
        .thenReturn(true);
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenThrow(new RuntimeException("token signing failed"));

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    person, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RuntimeException.class);
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void switchRoleToSelf() {
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    AuthenticationTokens tokens =
        roleSwitchService.switchRole(
            person, new SwitchRoleCommand(PERSON, person.getPersonalCode()));

    assertThat(tokens.accessToken()).isEqualTo("access");
  }

  @Test
  void switchToSelfPublishesAuditEvent() {
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, person.getPersonalCode()));

    verify(applicationEventPublisher).publishEvent(new RoleSwitchedEvent(person, person));
  }

  @Test
  void switchToSelfPublishesNoAuditEventWhenTokenGenerationFails() {
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenThrow(new RuntimeException("token signing failed"));

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    person, new SwitchRoleCommand(PERSON, person.getPersonalCode())))
        .isInstanceOf(RuntimeException.class);
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void switchRoleToSelfWithWrongCodeThrows() {
    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, "99999999999")))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void switchToCompanyThrowsWhenCompanyNotFound() {
    when(companyRoles.company("99999999")).thenThrow(new CompanyNotFoundException("99999999"));

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    person, new SwitchRoleCommand(LEGAL_ENTITY, "99999999")))
        .isInstanceOf(CompanyNotFoundException.class);
  }

  @Test
  void switchToCompanyThrowsWhenNotBoardMember() {
    when(companyRoles.company(SAMPLE_REGISTRY_CODE))
        .thenReturn(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME));
    when(companyRoles.isBoardMember(person.getPersonalCode(), SAMPLE_REGISTRY_CODE))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    person, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void shareholderWithoutBoardMembershipCannotSwitchRole() {
    when(companyRoles.company(SAMPLE_REGISTRY_CODE))
        .thenReturn(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME));
    when(companyRoles.isBoardMember(person.getPersonalCode(), SAMPLE_REGISTRY_CODE))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    person, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void getRolesReturnsSelfAndBoardMemberCompanies() {
    when(companyRoles.boardMemberCompanies(person.getPersonalCode()))
        .thenReturn(List.of(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME)));

    List<Role> result = roleSwitchService.getRoles(person);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().type()).isEqualTo(PERSON);
    assertThat(result.getFirst().code()).isEqualTo(person.getPersonalCode());
    assertThat(result.getFirst().name()).isEqualTo(person.getFullName());
    assertThat(result.getLast().type()).isEqualTo(LEGAL_ENTITY);
    assertThat(result.getLast().code()).isEqualTo(SAMPLE_REGISTRY_CODE);
    assertThat(result.getLast().name()).isEqualTo(SAMPLE_COMPANY_NAME);
  }

  @Test
  void getRolesExcludesCompaniesWhereUserIsOnlyShareholder() {
    when(companyRoles.boardMemberCompanies(person.getPersonalCode()))
        .thenReturn(List.of(new CompanyRole("11111111", SAMPLE_COMPANY_NAME)));

    List<Role> result = roleSwitchService.getRoles(person);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().type()).isEqualTo(PERSON);
    assertThat(result.getLast().code()).isEqualTo("11111111");
  }

  @Test
  void getRolesIncludesActivelyRepresentedChildren() {
    when(companyRoles.boardMemberCompanies(person.getPersonalCode())).thenReturn(List.of());
    when(childRepresentations.findActivelyRepresentedChildCodes(person.getPersonalCode()))
        .thenReturn(List.of(CHILD_CODE));
    when(principalUsers.fullName(CHILD_CODE)).thenReturn(Optional.of(CHILD_NAME));

    List<Role> result = roleSwitchService.getRoles(person);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().type()).isEqualTo(PERSON);
    assertThat(result.getFirst().code()).isEqualTo(person.getPersonalCode());
    Role childRole = result.getLast();
    assertThat(childRole.type()).isEqualTo(PERSON);
    assertThat(childRole.code()).isEqualTo(CHILD_CODE);
    assertThat(childRole.name()).isEqualTo("Mari Maasikas");
  }

  @Test
  void switchToActivelyRepresentedChildGeneratesTokens() {
    when(childRepresentations.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(true);
    when(principalUsers.fullName(CHILD_CODE)).thenReturn(Optional.of(CHILD_NAME));
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    AuthenticationTokens tokens =
        roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE));

    assertThat(tokens.accessToken()).isEqualTo("access");
  }

  @Test
  void switchToRepresentedChildPublishesAuditEvent() {
    when(childRepresentations.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(true);
    when(principalUsers.fullName(CHILD_CODE)).thenReturn(Optional.of(CHILD_NAME));
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE));

    verify(applicationEventPublisher).publishEvent(new RoleSwitchedEvent(person, person));
  }

  @Test
  void switchToRepresentedChildPublishesRoleSwitchedEventWithTheChildRole() {
    var switchedPerson =
        sampleAuthenticatedPersonAndMember()
            .role(new Role(PERSON, CHILD_CODE, "Mari Maasikas"))
            .build();
    when(childRepresentations.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(true);
    when(principalUsers.fullName(CHILD_CODE)).thenReturn(Optional.of(CHILD_NAME));
    when(principalService.withRole(any(), any())).thenReturn(switchedPerson);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE));

    verify(applicationEventPublisher).publishEvent(new RoleSwitchedEvent(person, switchedPerson));
  }

  @Test
  void switchToRepresentedChildPublishesNoAuditEventWhenTokenGenerationFails() {
    when(childRepresentations.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(true);
    when(principalUsers.fullName(CHILD_CODE)).thenReturn(Optional.of(CHILD_NAME));
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenThrow(new RuntimeException("token signing failed"));

    assertThatThrownBy(
            () -> roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE)))
        .isInstanceOf(RuntimeException.class);
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void switchToChildWithoutActiveLinkPublishesNoAuditEvent() {
    when(childRepresentations.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(false);

    assertThatThrownBy(
            () -> roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void switchToChildWithoutActiveLinkThrows() {
    when(childRepresentations.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(false);

    assertThatThrownBy(
            () -> roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void getPendingOnboardingsReturnsPendingChildrenWithNames() {
    when(childRepresentations.findPendingChildCodes(person.getPersonalCode()))
        .thenReturn(List.of(CHILD_CODE));
    when(principalUsers.fullName(CHILD_CODE)).thenReturn(Optional.of(CHILD_NAME));

    List<PendingOnboardingResponse> result = roleSwitchService.getPendingOnboardings(person);

    assertThat(result)
        .containsExactly(new PendingOnboardingResponse(PERSON, CHILD_CODE, "Mari Maasikas"));
  }

  @Test
  void getPendingOnboardingsIsEmptyWhenThereAreNoPendingLinks() {
    when(childRepresentations.findPendingChildCodes(person.getPersonalCode()))
        .thenReturn(List.of());

    assertThat(roleSwitchService.getPendingOnboardings(person)).isEmpty();
  }

  @Test
  void otherUserCannotSwitchToCompanyTheyAreNotLinkedTo() {
    var otherUser =
        AuthenticatedPerson.builder()
            .personalCode("39911223344")
            .firstName("Other")
            .lastName("User")
            .userId(999L)
            .build();
    when(companyRoles.company(SAMPLE_REGISTRY_CODE))
        .thenReturn(new CompanyRole(SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME));
    when(companyRoles.isBoardMember("39911223344", SAMPLE_REGISTRY_CODE)).thenReturn(false);

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    otherUser, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }
}
