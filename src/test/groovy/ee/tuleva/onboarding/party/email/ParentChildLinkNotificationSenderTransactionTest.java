package ee.tuleva.onboarding.party.email;

import static ee.tuleva.onboarding.notification.email.EmailStatus.SENT;
import static ee.tuleva.onboarding.notification.email.EmailType.PARENT_CHILD_LINK_CONFIRMATION;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.email.Email;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.persistence.EmailRepository;
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
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  ParentChildLinkNotificationSender.class,
  EmailPersistenceService.class,
  ParentChildLinkNotificationSenderTransactionTest.FixedClockConfig.class
})
class ParentChildLinkNotificationSenderTransactionTest {

  private static final Instant NOW = Instant.parse("2026-09-30T08:00:00Z");
  private static final String NEW_PARENT = "38812121215";
  private static final String PARENT_WITH_INVALID_PERSONAL_CODE = "38888888888";
  private static final String CHILD = "61506150006";
  private static final String CONFIRMATION_TEMPLATE = "parent_child_link_confirmation_et";
  private static final String LINK_ADDED_TEMPLATE = "parent_child_link_added_et";
  private static final List<String> TAGS = List.of("parent_child_link");
  private static final String CONFIRMATION_MESSAGE_ID = "mandrill-message-1";

  private final User newParent = user(NEW_PARENT, "New", "Parent", "parent@example.com");
  private final User parentWithInvalidPersonalCode =
      user(PARENT_WITH_INVALID_PERSONAL_CODE, "Other", "Parent", "other@example.com");
  private final User child = user(CHILD, "Baby", "Child", null);

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private EmailRepository emailRepository;
  @Autowired private ParentChildLinkRepository parentChildLinkRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private EmailService emailService;
  @MockitoBean private UserService userService;

  @AfterEach
  void cleanUp() {
    emailRepository.deleteAll();
    parentChildLinkRepository.deleteAll();
  }

  @Test
  void confirmationSentAfterTheLinkCommits_isRecordedAsASentEmail() {
    givenUsers(child, newParent);
    givenMandrillSends(newParent, CONFIRMATION_TEMPLATE, CONFIRMATION_MESSAGE_ID);

    publishLinkCreatedInACommittedTransaction();

    assertThat(emailRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
            "id", "createdDate", "updatedDate")
        .containsExactly(recordedConfirmation());
  }

  @Test
  void confirmationIsRecorded_evenWhenTheEmailToAnotherParentCannotBeRecorded() {
    parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(PARENT_WITH_INVALID_PERSONAL_CODE)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(LocalDate.of(2033, 6, 15))
            .build());
    givenUsers(child, newParent, parentWithInvalidPersonalCode);
    givenMandrillSends(newParent, CONFIRMATION_TEMPLATE, CONFIRMATION_MESSAGE_ID);
    givenMandrillSends(parentWithInvalidPersonalCode, LINK_ADDED_TEMPLATE, "mandrill-message-2");

    publishLinkCreatedInACommittedTransaction();

    assertThat(emailRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
            "id", "createdDate", "updatedDate")
        .containsExactly(recordedConfirmation());
  }

  private void publishLinkCreatedInACommittedTransaction() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                eventPublisher.publishEvent(
                    new ParentChildLinkCreatedEvent(NEW_PARENT, CHILD, LEGAL_REPRESENTATIVE)));
  }

  private void givenUsers(User... users) {
    Stream.of(users)
        .forEach(
            user ->
                given(userService.findByPersonalCode(user.getPersonalCode()))
                    .willReturn(Optional.of(user)));
  }

  private void givenMandrillSends(User recipient, String templateName, String messageId) {
    var message = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                eq(recipient.getEmail()), eq(templateName), any(), eq(TAGS)))
        .willReturn(message);
    var sentByMandrill = sentByMandrill(messageId);
    given(emailService.send(recipient, message, templateName))
        .willReturn(Optional.of(sentByMandrill));
  }

  private static Email recordedConfirmation() {
    return Email.builder()
        .personalCode(NEW_PARENT)
        .mandrillMessageId(CONFIRMATION_MESSAGE_ID)
        .type(PARENT_CHILD_LINK_CONFIRMATION)
        .status(SENT)
        .build();
  }

  private static MandrillMessageStatus sentByMandrill(String messageId) {
    var status = mock(MandrillMessageStatus.class);
    given(status.getId()).willReturn(messageId);
    given(status.getStatus()).willReturn("sent");
    return status;
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

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
