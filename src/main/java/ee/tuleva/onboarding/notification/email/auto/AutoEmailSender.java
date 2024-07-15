package ee.tuleva.onboarding.notification.email.auto;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.Emailable;
import ee.tuleva.onboarding.notification.email.provider.MailchimpService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
@RequiredArgsConstructor
@Profile("!dev")
@Slf4j
public class AutoEmailSender {

  private final Clock clock;
  private final List<AutoEmailRepository<?>> autoEmailRepositories;
  private final MailchimpService mailchimpService;
  private final EmailPersistenceService emailPersistenceService;

  // checks every day at 12:00
  @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Tallinn")
  public void sendMonthlyEmails() {
    for (final var autoEmailRepository : autoEmailRepositories) {
      EmailType emailType = autoEmailRepository.getEmailType();
      log.info("Checking monthly emails for: emailType={}", emailType);
      LocalDate startDate = LocalDate.now(clock).withDayOfMonth(1);
      LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
      final var emailablePeople = autoEmailRepository.fetch(startDate, endDate);
      log.info("Sending monthly emails: emailType={}, to={}", emailType, emailablePeople.size());
      int emailsSent = sendEmails(emailablePeople, emailType);
      log.info(
          "Successfully sent monthly emails: emailType={}, emailsSent={}", emailType, emailsSent);
    }
  }

  private <EmailablePerson extends Emailable & Person> int sendEmails(
      List<EmailablePerson> emailablePeople, EmailType emailType) {
    int emailsSent = 0;
    for (EmailablePerson emailablePerson : emailablePeople) {
      String personalCode = emailablePerson.getPersonalCode();
      var lastEmailSendDate =
          emailPersistenceService.getLastEmailSendDate(emailablePerson, emailType);
      if (lastEmailSendDate.isPresent()
          && lastEmailSendDate.get().isAfter(ZonedDateTime.now(clock).minusMonths(4).toInstant())) {
        log.info(
            "Already sent monthly email, skipping: personalCode={}, emailType={}",
            personalCode,
            emailType);
        continue;
      }
      log.info(
          "Sending monthly email to person: personalCode={}, emailType={}",
          personalCode,
          emailType);

      try {
        mailchimpService.sendEvent(
            emailablePerson.getEmail(), EmailEvent.getByEmailType(emailType));
        emailsSent++;
      } catch (HttpClientErrorException.NotFound e) {
        log.info(
            "Email not found in Mailchimp, skipping monthly email: personalCode={}, emailType={}",
            personalCode,
            emailType);
        continue;
      }
      emailPersistenceService.save(emailablePerson, emailType, SCHEDULED);
    }
    return emailsSent;
  }
}
