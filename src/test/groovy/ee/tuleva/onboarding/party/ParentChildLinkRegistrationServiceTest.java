package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.party.GuardianLinkRegistration.Created;
import ee.tuleva.onboarding.party.GuardianLinkRegistration.EndDateChanged;
import ee.tuleva.onboarding.party.GuardianLinkRegistration.Unchanged;
import ee.tuleva.onboarding.populationregister.Guardian;
import ee.tuleva.onboarding.user.NameChangeBasis;
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

@ExtendWith(MockitoExtension.class)
class ParentChildLinkRegistrationServiceTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final LocalDate CHILD_EIGHTEENTH_BIRTHDAY = LocalDate.of(2033, 6, 15);

  private static final String GUARDIAN_CODE = "38812121215";
  private static final String ADULT_WARD = "48806046007";
  private static final LocalDate GUARDIANSHIP_VALID_UNTIL = LocalDate.of(2099, 12, 31);

  private static final String CO_PARENT = "38002020008";
  private static final Guardian CO_PARENT_GUARDIAN =
      new Guardian(CO_PARENT, PROPERTY_CUSTODY, VALID, ALIVE, "Kati", "Karu");

  private static final NameChangeBasis FROM_POPULATION_REGISTER =
      NameChangeBasis.populationRegister();
  private static final NameChangeBasis FROM_COURT_RULING =
      NameChangeBasis.admin("Court ruling 2-26-1234");

  @Mock private ParentChildLinkRepository parentChildLinkRepository;
  @Mock private UserService userService;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);

  private ParentChildLinkRegistrationService service;

  @BeforeEach
  void setUp() {
    service = new ParentChildLinkRegistrationService(parentChildLinkRepository, userService, clock);
  }

  @Test
  void registersNewChildUserAndLink() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.empty());
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.empty());
    given(parentChildLinkRepository.save(any())).willAnswer(returnsFirstArg());

    ParentChildLink result =
        service.register(PARENT, CHILD, "mari", "maasikas", FROM_POPULATION_REGISTER);

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
                .build(),
            FROM_POPULATION_REGISTER);
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

    ParentChildLink result =
        service.register(PARENT, CHILD, "mari", "maasikas", FROM_POPULATION_REGISTER);

    assertThat(result).isSameAs(existingLink);
    verify(userService).changeName(CHILD, "Mari", "Maasikas", FROM_POPULATION_REGISTER);
    verify(parentChildLinkRepository, never()).save(any());
  }

  @Test
  void rejectsFutureDatedChild() {
    assertThatThrownBy(
            () -> service.register(PARENT, "59001010000", "Fu", "Ture", FROM_POPULATION_REGISTER))
        .isInstanceOf(ChildIsNotAMinorException.class);

    verifyNoInteractions(userService);
    verify(parentChildLinkRepository, never()).save(any());
  }

  @Test
  void rejectsAdultChild() {
    assertThatThrownBy(
            () -> service.register(PARENT, "38812121215", "Ad", "Ult", FROM_POPULATION_REGISTER))
        .isInstanceOf(ChildIsNotAMinorException.class);

    verifyNoInteractions(userService);
    verify(parentChildLinkRepository, never()).save(any());
  }

  @Test
  void registerPending_savesPendingLink() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.empty());
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.empty());
    given(parentChildLinkRepository.save(any())).willAnswer(returnsFirstArg());

    ParentChildLink saved = service.registerPending(CO_PARENT_GUARDIAN, CHILD, "mari", "maasikas");

    verify(userService)
        .createNewUser(
            User.builder()
                .personalCode(CHILD)
                .firstName("Mari")
                .lastName("Maasikas")
                .active(true)
                .build(),
            FROM_POPULATION_REGISTER);
    assertThat(saved.getParentPersonalCode()).isEqualTo(CO_PARENT);
    assertThat(saved.getChildPersonalCode()).isEqualTo(CHILD);
    assertThat(saved.getRelationshipType()).isEqualTo(LEGAL_REPRESENTATIVE);
    assertThat(saved.getValidUntil()).isEqualTo(CHILD_EIGHTEENTH_BIRTHDAY);
    assertThat(saved.isPending()).isTrue();
    assertThat(saved.getParentFirstName()).isEqualTo("Kati");
    assertThat(saved.getParentLastName()).isEqualTo("Karu");
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
            .status(ACTIVE)
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.of(existingActive));

    ParentChildLink result = service.registerPending(CO_PARENT_GUARDIAN, CHILD, "mari", "maasikas");

    assertThat(result).isSameAs(existingActive);
    assertThat(result.getStatus()).isEqualTo(ACTIVE);
    verify(parentChildLinkRepository, never()).save(any());
  }

  @Test
  void registerPending_recordsTheRegisterNameOnAnExistingLinkThatHasNone() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.empty());
    ParentChildLink existingPending =
        ParentChildLink.builder()
            .parentPersonalCode(CO_PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(CHILD_EIGHTEENTH_BIRTHDAY)
            .status(PENDING_KYC)
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.of(existingPending));
    given(parentChildLinkRepository.save(any())).willAnswer(returnsFirstArg());

    ParentChildLink result = service.registerPending(CO_PARENT_GUARDIAN, CHILD, "mari", "maasikas");

    assertThat(result.getParentFirstName()).isEqualTo("Kati");
    assertThat(result.getParentLastName()).isEqualTo("Karu");
    assertThat(result.isPending()).isTrue();
    verify(parentChildLinkRepository).save(existingPending);
  }

  @Test
  void registerPending_rejectsAdultChild() {
    assertThatThrownBy(
            () -> service.registerPending(CO_PARENT_GUARDIAN, "38812121215", "Ad", "Ult"))
        .isInstanceOf(ChildIsNotAMinorException.class);

    verifyNoInteractions(userService);
    verify(parentChildLinkRepository, never()).save(any());
  }

  @Test
  void register_activatesAnExistingPendingLink() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.empty());
    ParentChildLink pending =
        ParentChildLink.builder()
            .parentPersonalCode(PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(CHILD_EIGHTEENTH_BIRTHDAY)
            .status(PENDING_KYC)
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.of(pending));
    given(parentChildLinkRepository.save(any())).willAnswer(returnsFirstArg());

    ParentChildLink result =
        service.register(PARENT, CHILD, "mari", "maasikas", FROM_POPULATION_REGISTER);

    assertThat(result).isSameAs(pending);
    assertThat(result.getStatus()).isEqualTo(ACTIVE);
    verify(parentChildLinkRepository).save(pending);
  }

  @Test
  void registersGuardianLinkForAdultWard() {
    given(userService.findByPersonalCode(ADULT_WARD)).willReturn(Optional.empty());
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    GUARDIAN_CODE, ADULT_WARD, GUARDIAN))
        .willReturn(Optional.empty());
    given(parentChildLinkRepository.save(any())).willAnswer(returnsFirstArg());

    GuardianLinkRegistration registration =
        service.registerGuardian(
            GUARDIAN_CODE,
            ADULT_WARD,
            "ants",
            "haldja",
            GUARDIANSHIP_VALID_UNTIL,
            FROM_COURT_RULING);

    assertThat(registration).isInstanceOf(Created.class);
    ParentChildLink result = registration.link();
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
                .build(),
            FROM_COURT_RULING);
  }

  @Test
  void registerPending_changesAnExistingChildsNameAsThePopulationRegisterGaveIt() {
    given(userService.findByPersonalCode(CHILD))
        .willReturn(Optional.of(User.builder().id(7L).personalCode(CHILD).build()));
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    CO_PARENT, CHILD, LEGAL_REPRESENTATIVE))
        .willReturn(Optional.empty());
    given(parentChildLinkRepository.save(any())).willAnswer(returnsFirstArg());

    service.registerPending(CO_PARENT_GUARDIAN, CHILD, "mari", "maasikas");

    verify(userService).changeName(CHILD, "Mari", "Maasikas", FROM_POPULATION_REGISTER);
    verify(userService, never()).createNewUser(any(), any());
  }

  @Test
  void registerGuardian_changesAnExistingWardsNameNamingTheDocumentItIsBasedOn() {
    given(userService.findByPersonalCode(ADULT_WARD))
        .willReturn(Optional.of(User.builder().id(8L).personalCode(ADULT_WARD).build()));
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    GUARDIAN_CODE, ADULT_WARD, GUARDIAN))
        .willReturn(Optional.empty());
    given(parentChildLinkRepository.save(any())).willAnswer(returnsFirstArg());

    service.registerGuardian(
        GUARDIAN_CODE, ADULT_WARD, "ants", "haldja", GUARDIANSHIP_VALID_UNTIL, FROM_COURT_RULING);

    verify(userService).changeName(ADULT_WARD, "Ants", "Haldja", FROM_COURT_RULING);
    verify(userService, never()).createNewUser(any(), any());
  }

  @Test
  void registerGuardian_extendsTheEndDateOfAnExistingLinkEvenOnceItHasLapsed() {
    ParentChildLink existing = givenExisting(guardianLink(LocalDate.of(2026, 1, 31)).build());
    given(parentChildLinkRepository.save(existing)).willReturn(existing);

    GuardianLinkRegistration registration =
        service.registerGuardian(
            GUARDIAN_CODE,
            ADULT_WARD,
            "ants",
            "haldja",
            GUARDIANSHIP_VALID_UNTIL,
            FROM_COURT_RULING);

    assertThat(registration).isEqualTo(new EndDateChanged(existing, LocalDate.of(2026, 1, 31)));
    assertThat(existing.getValidUntil()).isEqualTo(GUARDIANSHIP_VALID_UNTIL);
    verify(parentChildLinkRepository).save(existing);
  }

  @Test
  void registerGuardian_shortensTheEndDateOfAnExistingLink() {
    ParentChildLink existing = givenExisting(guardianLink(GUARDIANSHIP_VALID_UNTIL).build());
    given(parentChildLinkRepository.save(existing)).willReturn(existing);

    GuardianLinkRegistration registration =
        service.registerGuardian(
            GUARDIAN_CODE,
            ADULT_WARD,
            "ants",
            "haldja",
            LocalDate.of(2027, 1, 31),
            FROM_COURT_RULING);

    assertThat(registration).isEqualTo(new EndDateChanged(existing, GUARDIANSHIP_VALID_UNTIL));
    assertThat(existing.getValidUntil()).isEqualTo(LocalDate.of(2027, 1, 31));
    verify(parentChildLinkRepository).save(existing);
  }

  @Test
  void registerGuardian_leavesALinkAlreadyEndingOnTheDateUnchanged() {
    ParentChildLink existing = givenExisting(guardianLink(GUARDIANSHIP_VALID_UNTIL).build());

    GuardianLinkRegistration registration =
        service.registerGuardian(
            GUARDIAN_CODE,
            ADULT_WARD,
            "ants",
            "haldja",
            GUARDIANSHIP_VALID_UNTIL,
            FROM_COURT_RULING);

    assertThat(registration).isEqualTo(new Unchanged(existing));
    verify(parentChildLinkRepository, never()).save(any());
  }

  @Test
  void registerGuardian_keepsASuspendedLinkSuspendedWhenItsEndDateChanges() {
    Instant suspendedAt = Instant.parse("2026-03-01T10:00:00Z");
    ParentChildLink existing =
        givenExisting(guardianLink(LocalDate.of(2027, 1, 31)).suspendedAt(suspendedAt).build());
    given(parentChildLinkRepository.save(existing)).willReturn(existing);

    GuardianLinkRegistration registration =
        service.registerGuardian(
            GUARDIAN_CODE,
            ADULT_WARD,
            "ants",
            "haldja",
            GUARDIANSHIP_VALID_UNTIL,
            FROM_COURT_RULING);

    assertThat(registration).isEqualTo(new EndDateChanged(existing, LocalDate.of(2027, 1, 31)));
    assertThat(existing.getSuspendedAt()).isEqualTo(suspendedAt);
    assertThat(existing.getValidUntil()).isEqualTo(GUARDIANSHIP_VALID_UNTIL);
  }

  private ParentChildLink.ParentChildLinkBuilder guardianLink(LocalDate validUntil) {
    return ParentChildLink.builder()
        .parentPersonalCode(GUARDIAN_CODE)
        .childPersonalCode(ADULT_WARD)
        .relationshipType(GUARDIAN)
        .validUntil(validUntil);
  }

  private ParentChildLink givenExisting(ParentChildLink existing) {
    given(userService.findByPersonalCode(ADULT_WARD)).willReturn(Optional.empty());
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                    GUARDIAN_CODE, ADULT_WARD, GUARDIAN))
        .willReturn(Optional.of(existing));
    return existing;
  }
}
