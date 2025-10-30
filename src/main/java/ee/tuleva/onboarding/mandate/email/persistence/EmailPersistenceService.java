package ee.tuleva.onboarding.mandate.email.persistence;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.*;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.Clock;
import java.time.Instant;
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
    return save(person, null, type, status.name(), (Mandate) null);
  }

  public Email save(Person person, String messageId, EmailType type, String status) {
    return save(person, messageId, type, status, (Mandate) null);
  }

  public boolean hasEmailsFor(Mandate mandate) {
    return !emailRepository.findAllByMandate(mandate).isEmpty();
  }

  public boolean hasEmailsFor(MandateBatch batch) {
    return !emailRepository.findAllByMandateBatch(batch).isEmpty();
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

  public Email save(
      Person person, String messageId, EmailType type, String status, MandateBatch mandateBatch) {
    Email scheduledEmail =
        Email.builder()
            .personalCode(person.getPersonalCode())
            .mandrillMessageId(messageId)
            .type(type)
            .status(EmailStatus.valueOf(status.toUpperCase()))
            .mandateBatch(mandateBatch)
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

  public boolean hasEmailsToday(Person person, EmailType type, Mandate mandate) {
    var statuses = List.of(SENT, QUEUED, SCHEDULED);

    if (mandate.isPartOfBatch()) {
      Optional<Email> latestBatchEmail =
          emailRepository
              .findFirstByPersonalCodeAndTypeAndMandateBatchAndStatusInOrderByCreatedDateDesc(
                  person.getPersonalCode(), type, mandate.getMandateBatch(), statuses);

      return latestBatchEmail.map(email -> email.isToday(clock)).orElse(false);
    }

    Optional<Email> latestMandateEmail =
        emailRepository.findFirstByPersonalCodeAndTypeAndMandateAndStatusInOrderByCreatedDateDesc(
            person.getPersonalCode(), type, mandate, statuses);

    return latestMandateEmail.map(email -> email.isToday(clock)).orElse(false);
  }

  public Optional<Instant> getLastEmailSendDate(Person person, EmailType type) {
    return emailRepository
        .findFirstByPersonalCodeAndTypeOrderByCreatedDateDesc(person.getPersonalCode(), type)
        .map(Email::getCreatedDate);
  }

  private List<Email> getScheduledEmails(Person person, EmailType type) {
    return emailRepository.findAllByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDesc(
        person.getPersonalCode(), type, List.of(SCHEDULED, QUEUED));
  }

  public boolean hasEmailTypeBeenSentBefore(EmailType type) {
    return emailRepository.existsByType(type);
  }
}
