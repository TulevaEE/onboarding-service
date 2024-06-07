package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS;

import ee.tuleva.onboarding.analytics.leavers.AnalyticsLeaver;
import ee.tuleva.onboarding.analytics.leavers.AnalyticsLeaversRepository;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
  private final AnalyticsLeaversRepository leaversRepository;
  private final MailchimpService mailchimpService;
  private final EmailPersistenceService emailPersistenceService;

  // checks every day at 12:00
  @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Tallinn")
  public void sendMonthlyLeaverEmail() {
    log.info("Checking leavers");
    LocalDate startDate = LocalDate.now(clock).withDayOfMonth(1);
    LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
    List<AnalyticsLeaver> leavers = leaversRepository.fetchLeavers(startDate, endDate);
    log.info("Sending leaver email to {} leavers", leavers.size());
    int emailsSent = 0;

    for (AnalyticsLeaver leaver : leavers) {
      if (leaver.lastEmailSentDate() != null
          && leaver.lastEmailSentDate().isAfter(LocalDateTime.now(clock).minusMonths(4))) {
        log.info("Already sent email to leaver, skipping {}", leaver.personalCode());
        continue;
      }
      if (emailPersistenceService.hasEmailsToday(leaver, SECOND_PILLAR_LEAVERS)) {
        log.info(
            "Leaver already has email today, skipping: leaver={}, emailType={}",
            leaver.personalCode(),
            SECOND_PILLAR_LEAVERS);
        continue;
      }
      log.info("Sending email to leaver {}", leaver.personalCode());

      try {
        mailchimpService.sendEvent(leaver.email(), "new_leaver");
        emailsSent++;
      } catch (HttpClientErrorException.NotFound e) {
        log.info("Leaver not found in Mailchimp, skipping {}", leaver.personalCode());
        continue;
      }
      emailPersistenceService.save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
    }

    log.info("Successfully sent leaver emails: {}", emailsSent);
  }
}
