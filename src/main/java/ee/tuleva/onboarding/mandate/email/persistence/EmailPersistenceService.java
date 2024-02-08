package ee.tuleva.onboarding.mandate.email.persistence;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.*;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailPersistenceService {

  private final EmailRepository emailRepository;
  private final EmailService emailService;
  private final Clock clock;

  public void save(User user, String messageId, EmailType type, String status) {
    save(user, messageId, type, status, null);
  }

  public void save(User user, String messageId, EmailType type, String status, Mandate mandate) {
    Email scheduledEmail =
        Email.builder()
            .userId(user.getId())
            .mandrillMessageId(messageId)
            .type(type)
            .status(EmailStatus.valueOf(status.toUpperCase()))
            .mandate(mandate)
            .build();
    log.info("Saving an email: email={}", scheduledEmail);
    emailRepository.save(scheduledEmail);
  }

  public List<Email> cancel(User user, EmailType type) {
    List<Email> scheduledEmails = getScheduledEmails(user, type);
    log.info("Cancelling scheduled emails: emails={}", scheduledEmails);
    List<Email> cancelled = new ArrayList<>();
    scheduledEmails.forEach(
        email ->
            emailService
                .cancelScheduledEmail(email.getMandrillMessageId())
                .ifPresent(
                    info -> {
                      email.setStatus(CANCELLED);
                      cancelled.add(email);
                    }));
    emailRepository.saveAll(scheduledEmails);
    return cancelled;
  }

  public boolean hasEmailsToday(User user, EmailType type) {
    var statuses = List.of(SENT, QUEUED, SCHEDULED);
    Optional<Email> latestEmail =
        emailRepository.findFirstByUserIdAndTypeAndStatusInOrderByCreatedDateDesc(
            user.getId(), type, statuses);
    return latestEmail.map(email -> email.isToday(clock)).orElse(false);
  }

  private List<Email> getScheduledEmails(User user, EmailType type) {
    return emailRepository.findAllByUserIdAndTypeAndStatusOrderByCreatedDateDesc(
        user.getId(), type, SCHEDULED);
  }
}
