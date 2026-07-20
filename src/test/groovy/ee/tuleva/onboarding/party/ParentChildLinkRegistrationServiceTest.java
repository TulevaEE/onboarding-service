package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ParentChildLinkRegistrationServiceTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final LocalDate CHILD_EIGHTEENTH_BIRTHDAY = LocalDate.of(2033, 6, 15);

  private static final String GUARDIAN_CODE = "38812121215";
  private static final String ADULT_WARD = "48806046007";
  private static final LocalDate GUARDIANSHIP_VALID_UNTIL = LocalDate.of(2099, 12, 31);

  private static final String CO_PARENT = "38002020008";

  @Mock private ParentChildLinkRepository parentChildLinkRepository;
  @Mock private UserService userService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);

  private ParentChildLinkRegistrationService service;

  @BeforeEach
  void setUp() {
    service =
        new ParentChildLinkRegistrationService(
            parentChildLinkRepository, userService, applicationEventPublisher, clock);
  }

  @Test
  void registersNewChildUserAndLink() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.empty());
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.empty());
    given(parentChildLinkRepository.save(org.mockito.ArgumentMatchers.any()))
        .willAnswer(returnsFirstArg());

    ParentChildLink result = service.register(PARENT, CHILD, "mari", "maasikas");

    assertThat(result.getParentPersonalCode()).isEqualTo(PARENT);
    assertThat(result.getChildPersonalCode()).isEqualTo(CHILD);
    assertThat(result.getRelationshipType()).isEqualTo(LEGAL_REPRESENTATIVE);
    assertThat(result.getValidUntil()).isEqualTo(CHILD_EIGHTEENTH_BIRTHDAY);
    assertThat(result.isSuspended()).isFalse();

    verify(userService)
        .createNewUser(
            User.builder()
                .personalCode(CHILD)
                .firstName("Mari")
                .lastName("Maasikas")
                .active(true)
                .build());
    verify(applicationEventPublisher)
        .publishEvent(new ParentChildLinkCreatedEvent(PARENT, CHILD, LEGAL_REPRESENTATIVE));
  }

  @Test
  void refreshesExistingChildNameAndKeepsLinkIdempotent() {
    User existing =
        User.builder()
            .id(7L)
            .personalCode(CHILD)
            .firstName("Old")
            .lastName("Name")
            .active(true)
            .build();
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.of(existing));
    ParentChildLink existingLink =
        ParentChildLink.builder()
            .parentPersonalCode(PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(CHILD_EIGHTEENTH_BIRTHDAY)
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.of(existingLink));

    ParentChildLink result = service.register(PARENT, CHILD, "mari", "maasikas");

    assertThat(result).isSameAs(existingLink);
    verify(userService)
        .save(
            User.builder()
                .id(7L)
                .personalCode(CHILD)
                .firstName("Mari")
                .lastName("Maasikas")
                .active(true)
                .build());
    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void rejectsFutureDatedChild() {
    assertThatThrownBy(() -> service.register(PARENT, "59001010002", "Fu", "Ture"))
        .isInstanceOf(ChildIsNotAMinorException.class);

    verifyNoInteractions(userService);
    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsAdultChild() {
    assertThatThrownBy(() -> service.register(PARENT, "38812121215", "Ad", "Ult"))
        .isInstanceOf(ChildIsNotAMinorException.class);

    verifyNoInteractions(userService);
    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void registerPending_savesPendingLinkWithoutPublishingAnEvent() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.empty());
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.empty());
    given(parentChildLinkRepository.save(org.mockito.ArgumentMatchers.any()))
        .willAnswer(returnsFirstArg());

    service.registerPending(CO_PARENT, CHILD, "mari", "maasikas");

    verify(userService)
        .createNewUser(
            User.builder()
                .personalCode(CHILD)
                .firstName("Mari")
                .lastName("Maasikas")
                .active(true)
                .build());
    var captor = org.mockito.ArgumentCaptor.forClass(ParentChildLink.class);
    verify(parentChildLinkRepository).save(captor.capture());
    ParentChildLink saved = captor.getValue();
    assertThat(saved.getParentPersonalCode()).isEqualTo(CO_PARENT);
    assertThat(saved.getChildPersonalCode()).isEqualTo(CHILD);
    assertThat(saved.getRelationshipType()).isEqualTo(LEGAL_REPRESENTATIVE);
    assertThat(saved.getValidUntil()).isEqualTo(CHILD_EIGHTEENTH_BIRTHDAY);
    assertThat(saved.isPending()).isTrue();
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void registerPending_isIdempotentAndNeverDowngradesAnExistingLink() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.empty());
    ParentChildLink existingActive =
        ParentChildLink.builder()
            .parentPersonalCode(CO_PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(CHILD_EIGHTEENTH_BIRTHDAY)
            .status(ParentChildLinkStatus.ACTIVE)
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.of(existingActive));

    service.registerPending(CO_PARENT, CHILD, "mari", "maasikas");

    assertThat(existingActive.isActive()).isTrue();
    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void registerPending_rejectsAdultChild() {
    assertThatThrownBy(() -> service.registerPending(CO_PARENT, "38812121215", "Ad", "Ult"))
        .isInstanceOf(ChildIsNotAMinorException.class);

    verifyNoInteractions(userService, applicationEventPublisher);
    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void activate_flipsPendingLinkToActiveAndPublishesEvent() {
    ParentChildLink pending =
        ParentChildLink.builder()
            .parentPersonalCode(CO_PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(CHILD_EIGHTEENTH_BIRTHDAY)
            .status(ParentChildLinkStatus.PENDING_KYC)
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.of(pending));

    service.activate(CO_PARENT, CHILD);

    assertThat(pending.isActive()).isTrue();
    verify(parentChildLinkRepository).save(pending);
    verify(applicationEventPublisher)
        .publishEvent(new ParentChildLinkCreatedEvent(CO_PARENT, CHILD, LEGAL_REPRESENTATIVE));
  }

  @Test
  void activate_isNoOpWhenLinkIsAlreadyActive() {
    ParentChildLink active =
        ParentChildLink.builder()
            .parentPersonalCode(CO_PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(CHILD_EIGHTEENTH_BIRTHDAY)
            .status(ParentChildLinkStatus.ACTIVE)
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.of(active));

    service.activate(CO_PARENT, CHILD);

    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void activate_isNoOpWhenNoLinkExists() {
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.empty());

    service.activate(CO_PARENT, CHILD);

    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(applicationEventPublisher);
  }

  @Test
  void registersGuardianLinkForAdultWard() {
    given(userService.findByPersonalCode(ADULT_WARD)).willReturn(Optional.empty());
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    GUARDIAN_CODE, ADULT_WARD, GUARDIAN))
        .willReturn(Optional.empty());
    given(parentChildLinkRepository.save(org.mockito.ArgumentMatchers.any()))
        .willAnswer(returnsFirstArg());

    ParentChildLink result =
        service.registerGuardian(
            GUARDIAN_CODE, ADULT_WARD, "ants", "haldja", GUARDIANSHIP_VALID_UNTIL);

    assertThat(result.getParentPersonalCode()).isEqualTo(GUARDIAN_CODE);
    assertThat(result.getChildPersonalCode()).isEqualTo(ADULT_WARD);
    assertThat(result.getRelationshipType()).isEqualTo(GUARDIAN);
    assertThat(result.getValidUntil()).isEqualTo(GUARDIANSHIP_VALID_UNTIL);

    verify(userService)
        .createNewUser(
            User.builder()
                .personalCode(ADULT_WARD)
                .firstName("Ants")
                .lastName("Haldja")
                .active(true)
                .build());
    verify(applicationEventPublisher)
        .publishEvent(new ParentChildLinkCreatedEvent(GUARDIAN_CODE, ADULT_WARD, GUARDIAN));
  }
}
