package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.role.RoleType.*;
import static ee.tuleva.onboarding.company.CompanyFixture.*;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.TokenService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import ee.tuleva.onboarding.company.CompanyPartyRepository;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RoleSwitchServiceTest {

  @Mock private CompanyRepository companyRepository;
  @Mock private CompanyPartyRepository companyPartyRepository;
  @Mock private PrincipalService principalService;
  @Mock private TokenService tokenService;
  @Mock private ParentChildLinkService parentChildLinkService;
  @Mock private UserService userService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @InjectMocks private RoleSwitchService roleSwitchService;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();

  private static final String CHILD_CODE = "61506150006";

  private static User childUser() {
    return User.builder()
        .id(42L)
        .personalCode(CHILD_CODE)
        .firstName("Mari")
        .lastName("Maasikas")
        .active(true)
        .build();
  }

  @Test
  void switchRoleToCompany() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
            person.getPersonalCode(), PartyId.Type.PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
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
  void switchRoleToSelfWithWrongCodeThrows() {
    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, "99999999999")))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void switchToCompanyThrowsWhenCompanyNotFound() {
    when(companyRepository.findByRegistryCode("99999999")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    person, new SwitchRoleCommand(LEGAL_ENTITY, "99999999")))
        .isInstanceOf(CompanyNotFoundException.class);
  }

  @Test
  void switchToCompanyThrowsWhenNotBoardMember() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
            person.getPersonalCode(), PartyId.Type.PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    person, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void shareholderWithoutBoardMembershipCannotSwitchRole() {
    var company = sampleCompany().build();
    when(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .thenReturn(Optional.of(company));
    when(companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
            person.getPersonalCode(), PartyId.Type.PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    person, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }

  @Test
  void getRolesReturnsSelfAndBoardMemberCompanies() {
    var company = sampleCompany().build();
    var membership = sampleBoardMembership(person.getPersonalCode()).build();
    when(companyPartyRepository.findByPartyCodeAndPartyTypeAndRelationshipType(
            person.getPersonalCode(), PartyId.Type.PERSON, BOARD_MEMBER))
        .thenReturn(List.of(membership));
    when(companyRepository.findAllById(List.of(SAMPLE_COMPANY_ID))).thenReturn(List.of(company));

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
    var company = sampleCompany().registryCode("11111111").build();
    var membership =
        sampleBoardMembership(person.getPersonalCode()).companyId(company.getId()).build();
    when(companyPartyRepository.findByPartyCodeAndPartyTypeAndRelationshipType(
            person.getPersonalCode(), PartyId.Type.PERSON, BOARD_MEMBER))
        .thenReturn(List.of(membership));
    when(companyRepository.findAllById(List.of(company.getId()))).thenReturn(List.of(company));

    List<Role> result = roleSwitchService.getRoles(person);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().type()).isEqualTo(PERSON);
    assertThat(result.getLast().code()).isEqualTo("11111111");
  }

  @Test
  void getRolesIncludesActivelyRepresentedChildren() {
    when(companyPartyRepository.findByPartyCodeAndPartyTypeAndRelationshipType(
            person.getPersonalCode(), PartyId.Type.PERSON, BOARD_MEMBER))
        .thenReturn(List.of());
    when(parentChildLinkService.findActivelyRepresentedChildCodes(person.getPersonalCode()))
        .thenReturn(List.of(CHILD_CODE));
    when(userService.findByPersonalCode(CHILD_CODE)).thenReturn(Optional.of(childUser()));

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
    when(parentChildLinkService.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(true);
    when(userService.findByPersonalCode(CHILD_CODE)).thenReturn(Optional.of(childUser()));
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    AuthenticationTokens tokens =
        roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE));

    assertThat(tokens.accessToken()).isEqualTo("access");
  }

  @Test
  void switchToRepresentedChildPublishesAuditEvent() {
    when(parentChildLinkService.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(true);
    when(userService.findByPersonalCode(CHILD_CODE)).thenReturn(Optional.of(childUser()));
    when(principalService.withRole(any(), any())).thenReturn(person);
    when(tokenService.generateTokens(any()))
        .thenReturn(new AuthenticationTokens("access", "refresh"));

    roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE));

    verify(applicationEventPublisher)
        .publishEvent(
            new TrackableEvent(
                person,
                TrackableEventType.REPRESENT_MINOR_ROLE_SWITCH,
                Map.of("childPersonalCode", CHILD_CODE)));
  }

  @Test
  void switchToRepresentedChildPublishesNoAuditEventWhenTokenGenerationFails() {
    when(parentChildLinkService.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(true);
    when(userService.findByPersonalCode(CHILD_CODE)).thenReturn(Optional.of(childUser()));
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
    when(parentChildLinkService.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(false);

    assertThatThrownBy(
            () -> roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void switchToChildWithoutActiveLinkThrows() {
    when(parentChildLinkService.isActiveRepresentation(person.getPersonalCode(), CHILD_CODE))
        .thenReturn(false);

    assertThatThrownBy(
            () -> roleSwitchService.switchRole(person, new SwitchRoleCommand(PERSON, CHILD_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
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
    when(companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
            "39911223344", PartyId.Type.PERSON, SAMPLE_COMPANY_ID, BOARD_MEMBER))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                roleSwitchService.switchRole(
                    otherUser, new SwitchRoleCommand(LEGAL_ENTITY, SAMPLE_REGISTRY_CODE)))
        .isInstanceOf(RoleSwitchAccessDeniedException.class);
  }
}
