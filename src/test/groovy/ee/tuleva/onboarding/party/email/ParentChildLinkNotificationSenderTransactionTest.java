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
import ee.tuleva.onboarding.party.ParentChildLinkCreatedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
  private static final String CHILD = "61506150006";
  private static final String CONFIRMATION_TEMPLATE = "parent_child_link_confirmation_et";
  private static final String MANDRILL_MESSAGE_ID = "mandrill-message-1";

  private final User newParent = user(NEW_PARENT, "New", "Parent", "parent@example.com");
  private final User child = user(CHILD, "Baby", "Child", null);

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private EmailRepository emailRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private EmailService emailService;
  @MockitoBean private UserService userService;

  @AfterEach
  void cleanUp() {
    emailRepository.deleteAll();
  }

  @Test
  void confirmationSentAfterTheLinkCommits_isRecordedAsASentEmail() {
    given(userService.findByPersonalCode(CHILD)).willReturn(Optional.of(child));
    given(userService.findByPersonalCode(NEW_PARENT)).willReturn(Optional.of(newParent));
    var confirmation = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                eq("parent@example.com"),
                eq(CONFIRMATION_TEMPLATE),
                any(),
                eq(List.of("parent_child_link"))))
        .willReturn(confirmation);
    var sentByMandrill = sentByMandrill();
    given(emailService.send(newParent, confirmation, CONFIRMATION_TEMPLATE))
        .willReturn(Optional.of(sentByMandrill));

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                eventPublisher.publishEvent(
                    new ParentChildLinkCreatedEvent(NEW_PARENT, CHILD, LEGAL_REPRESENTATIVE)));

    assertThat(emailRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
            "id", "createdDate", "updatedDate")
        .containsExactly(
            Email.builder()
                .personalCode(NEW_PARENT)
                .mandrillMessageId(MANDRILL_MESSAGE_ID)
                .type(PARENT_CHILD_LINK_CONFIRMATION)
                .status(SENT)
                .build());
  }

  private static MandrillMessageStatus sentByMandrill() {
    var status = mock(MandrillMessageStatus.class);
    given(status.getId()).willReturn(MANDRILL_MESSAGE_ID);
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
