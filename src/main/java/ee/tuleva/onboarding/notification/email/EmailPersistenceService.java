package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.notification.email.EmailStatus.*;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.persistence.EmailRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailPersistenceService {

  private final EmailRepository emailRepository;
  private final EmailService emailService;
  private final Clock clock;

  public Email save(Person person, EmailType type, EmailStatus status) {
    return save(person, null, type, status.name(), null, null);
  }

  public Email save(Person person, String messageId, EmailType type, String status) {
    return save(person, messageId, type, status, null, null);
  }

  public boolean hasEmailsForMandate(Long mandateId) {
    return !emailRepository.findAllByMandateId(mandateId).isEmpty();
  }

  public boolean hasEmailsForMandateBatch(Long mandateBatchId) {
    return !emailRepository.findAllByMandateBatchId(mandateBatchId).isEmpty();
  }

  public Email saveWithMandate(
      Person person, String messageId, EmailType type, String status, Long mandateId) {
    return save(person, messageId, type, status, mandateId, null);
  }

  public Email saveWithMandateBatch(
      Person person, String messageId, EmailType type, String status, Long mandateBatchId) {
    return save(person, messageId, type, status, null, mandateBatchId);
  }

  private Email save(
      Person person,
      @Nullable String messageId,
      EmailType type,
      String status,
      @Nullable Long mandateId,
      @Nullable Long mandateBatchId) {
    Email scheduledEmail =
        Email.builder()
            .personalCode(person.getPersonalCode())
            .mandrillMessageId(messageId)
            .type(type)
            .status(EmailStatus.valueOf(status.toUpperCase()))
            .mandateId(mandateId)
            .mandateBatchId(mandateBatchId)
            .build();
    log.info("Saving an email: email={}", scheduledEmail);
    try {
      Email savedEmail = emailRepository.save(scheduledEmail);
      log.info("Email saved successfully: savedEmail={}", savedEmail);
      return savedEmail;
    } catch (Exception e) {
      log.error("Failed to save email: email={}", scheduledEmail, e);
      throw e;
    }
  }

  public Optional<Email> findByMandrillMessageId(String mandrillMessageId) {
    return emailRepository.findByMandrillMessageId(mandrillMessageId);
  }

  public List<Email> cancel(Person person, EmailType type) {
    List<Email> scheduledEmails = getScheduledEmails(person, type);
    log.info("Cancelling scheduled emails: emails={}", scheduledEmails);
    List<Email> cancelled = new ArrayList<>();
    scheduledEmails.forEach(
        email ->
            emailService
                .cancelScheduledEmail(
                    requireNonNull(
                        email.getMandrillMessageId(),
                        "Scheduled email is missing a mandrillMessageId: emailId=" + email.getId()))
                .ifPresent(
                    info -> {
                      email.setStatus(CANCELLED);
                      cancelled.add(email);
                    }));
    emailRepository.saveAll(scheduledEmails);
    return cancelled;
  }

  public boolean hasMandateEmailsToday(Person person, EmailType type, Long mandateId) {
    var statuses = List.of(SENT, QUEUED, SCHEDULED);
    return emailRepository
        .findFirstByPersonalCodeAndTypeAndMandateIdAndStatusInOrderByCreatedDateDescIdDesc(
            person.getPersonalCode(), type, mandateId, statuses)
        .map(email -> email.isToday(clock))
        .orElse(false);
  }

  public boolean hasMandateBatchEmailsToday(Person person, EmailType type, Long mandateBatchId) {
    var statuses = List.of(SENT, QUEUED, SCHEDULED);
    return emailRepository
        .findFirstByPersonalCodeAndTypeAndMandateBatchIdAndStatusInOrderByCreatedDateDescIdDesc(
            person.getPersonalCode(), type, mandateBatchId, statuses)
        .map(email -> email.isToday(clock))
        .orElse(false);
  }

  public Optional<Instant> getLastEmailSendDate(Person person, EmailType type) {
    return emailRepository
        .findFirstByPersonalCodeAndTypeOrderByCreatedDateDescIdDesc(person.getPersonalCode(), type)
        .map(Email::getCreatedDate);
  }

  private List<Email> getScheduledEmails(Person person, EmailType type) {
    return emailRepository.findAllByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDescIdDesc(
        person.getPersonalCode(), type, List.of(SCHEDULED, QUEUED));
  }

  public boolean hasEmailTypeBeenSentBefore(EmailType type) {
    return emailRepository.existsByType(type);
  }
}
