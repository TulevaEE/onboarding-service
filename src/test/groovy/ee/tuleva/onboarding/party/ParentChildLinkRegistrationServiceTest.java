package ee.tuleva.onboarding.party;

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

  @Mock private ParentChildLinkRepository parentChildLinkRepository;
  @Mock private UserService userService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);

  private ParentChildLinkRegistrationService service;

  @BeforeEach
  void setUp() {
    service =
        new ParentChildLinkRegistrationService(
            parentChildLinkRepository, userService, clock, eventPublisher);
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

    ParentChildLink result =
        service.register(PARENT, CHILD, "mari", "maasikas", LEGAL_REPRESENTATIVE);

    assertThat(result.getParentPersonalCode()).isEqualTo(PARENT);
    assertThat(result.getChildPersonalCode()).isEqualTo(CHILD);
    assertThat(result.getRelationshipType()).isEqualTo(LEGAL_REPRESENTATIVE);
    assertThat(result.getValidUntil()).isEqualTo(CHILD_EIGHTEENTH_BIRTHDAY);

    verify(userService)
        .createNewUser(
            User.builder()
                .personalCode(CHILD)
                .firstName("Mari")
                .lastName("Maasikas")
                .active(true)
                .build());
    verify(eventPublisher).publishEvent(new ParentChildLinkRegisteredEvent(CHILD));
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
        service.register(PARENT, CHILD, "mari", "maasikas", LEGAL_REPRESENTATIVE);

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
    verify(eventPublisher).publishEvent(new ParentChildLinkRegisteredEvent(CHILD));
  }

  @Test
  void rejectsFutureDatedChild() {
    assertThatThrownBy(
            () -> service.register(PARENT, "59001010002", "Fu", "Ture", LEGAL_REPRESENTATIVE))
        .isInstanceOf(ChildIsNotAMinorException.class);

    verifyNoInteractions(userService);
    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsAdultChild() {
    assertThatThrownBy(
            () -> service.register(PARENT, "38812121215", "Ad", "Ult", LEGAL_REPRESENTATIVE))
        .isInstanceOf(ChildIsNotAMinorException.class);

    verifyNoInteractions(userService);
    verify(parentChildLinkRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }
}
