package ee.tuleva.onboarding.mandate.email.persistence;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.*;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.notification.email.EmailService;
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

  public Email save(Person person, EmailType type, EmailStatus status) {
    return save(person, null, type, status.name(), null);
  }

  public Email save(Person person, String messageId, EmailType type, String status) {
    return save(person, messageId, type, status, null);
  }

  public Email save(
      Person person, String messageId, EmailType type, String status, Mandate mandate) {
    Email scheduledEmail =
        Email.builder()
            .personalCode(person.getPersonalCode())
            .mandrillMessageId(messageId)
            .type(type)
            .status(EmailStatus.valueOf(status.toUpperCase()))
            .mandate(mandate)
            .build();
    log.info("Saving an email: email={}", scheduledEmail);
    return emailRepository.save(scheduledEmail);
  }

  public List<Email> cancel(Person person, EmailType type) {
    List<Email> scheduledEmails = getScheduledEmails(person, type);
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

  public boolean hasEmailsToday(Person person, EmailType type) {
    return hasEmailsToday(person, type, null);
  }

  public boolean hasEmailsToday(Person person, EmailType type, Mandate mandate) {
    var statuses = List.of(SENT, QUEUED, SCHEDULED);
    Optional<Email> latestEmail =
        emailRepository.findFirstByPersonalCodeAndTypeAndMandateAndStatusInOrderByCreatedDateDesc(
            person.getPersonalCode(), type, mandate, statuses);
    return latestEmail.map(email -> email.isToday(clock)).orElse(false);
  }

  private List<Email> getScheduledEmails(Person person, EmailType type) {
    return emailRepository.findAllByPersonalCodeAndTypeAndStatusOrderByCreatedDateDesc(
        person.getPersonalCode(), type, SCHEDULED);
  }
}
