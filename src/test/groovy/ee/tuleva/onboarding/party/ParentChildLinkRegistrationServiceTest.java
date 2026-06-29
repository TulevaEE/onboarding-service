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

@ExtendWith(MockitoExtension.class)
class ParentChildLinkRegistrationServiceTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final LocalDate CHILD_EIGHTEENTH_BIRTHDAY = LocalDate.of(2033, 6, 15);

  private static final String GUARDIAN_CODE = "38812121215";
  private static final String ADULT_WARD = "48806046007";
  private static final LocalDate GUARDIANSHIP_VALID_UNTIL = LocalDate.of(2099, 12, 31);

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
  }
}
