package ee.tuleva.onboarding.party.email;

import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkCreatedEvent;
import ee.tuleva.onboarding.party.ParentChildLinkRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentChildLinkNotificationSenderTest {

  private static final String NEW_PARENT = "38812121215";
  private static final String EXISTING_PARENT = "38501010002";
  private static final String CHILD = "61506150006";

  private static final List<String> TAGS = List.of("parent_child_link");

  @Mock private EmailService emailService;
  @Mock private EmailPersistenceService emailPersistenceService;
  @Mock private UserService userService;
  @Mock private ParentChildLinkRepository parentChildLinkRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);

  private ParentChildLinkNotificationSender sender;

  private final User newParent = user(NEW_PARENT, "New", "Parent", "new.parent@example.com");
  private final User existingParent =
      user(EXISTING_PARENT, "Old", "Guard", "old.guard@example.com");
  private final User child = user(CHILD, "Baby", "Child", null);

  @BeforeEach
  void setUp() {
    sender =
        new ParentChildLinkNotificationSender(
            emailService, emailPersistenceService, userService, parentChildLinkRepository, clock);
  }

  @Test
  void notifiesNewParentAndExistingParentsExcludingSelf() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.of(child));
    given(userService.findByPersonalCode(NEW_PARENT)).willReturn(Optional.of(newParent));
    given(userService.findByPersonalCode(EXISTING_PARENT)).willReturn(Optional.of(existingParent));
    given(
            parentChildLinkRepository.findByChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                CHILD, LocalDate.of(2026, 5, 22)))
        .willReturn(List.of(link(EXISTING_PARENT), link(NEW_PARENT)));

    MandrillMessage confirmationMessage = new MandrillMessage();
    MandrillMessage addedMessage = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                eq("new.parent@example.com"),
                eq("parent_child_link_confirmation_et"),
                eq(Map.of("fname", "New", "lname", "Parent", "childName", "Baby Child")),
                eq(TAGS),
                isNull()))
        .willReturn(confirmationMessage);
    given(
            emailService.newMandrillMessage(
                eq("old.guard@example.com"),
                eq("parent_child_link_added_et"),
                eq(
                    Map.of(
                        "fname", "Old",
                        "lname", "Guard",
                        "newRepresentativeName", "New Parent",
                        "childName", "Baby Child")),
                eq(TAGS),
                isNull()))
        .willReturn(addedMessage);

    sender.onParentChildLinkCreated(
        new ParentChildLinkCreatedEvent(NEW_PARENT, CHILD, LEGAL_REPRESENTATIVE));

    verify(emailService).send(newParent, confirmationMessage, "parent_child_link_confirmation_et");
    verify(emailService).send(existingParent, addedMessage, "parent_child_link_added_et");
    verify(emailService, never()).send(eq(newParent), any(), eq("parent_child_link_added_et"));
  }

  @Test
  void skipsNotificationsWhenNewParentIsNotAKnownUser() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.of(child));
    given(userService.findByPersonalCode(NEW_PARENT)).willReturn(Optional.empty());

    sender.onParentChildLinkCreated(
        new ParentChildLinkCreatedEvent(NEW_PARENT, CHILD, LEGAL_REPRESENTATIVE));

    verifyNoInteractions(emailService, emailPersistenceService, parentChildLinkRepository);
  }

  private ParentChildLink link(String parentPersonalCode) {
    return ParentChildLink.builder()
        .parentPersonalCode(parentPersonalCode)
        .childPersonalCode(CHILD)
        .relationshipType(LEGAL_REPRESENTATIVE)
        .validUntil(LocalDate.of(2033, 6, 15))
        .build();
  }

  private static User user(String personalCode, String firstName, String lastName, String email) {
    return User.builder()
        .personalCode(personalCode)
        .firstName(firstName)
        .lastName(lastName)
        .email(email)
        .active(true)
        .build();
  }
}
