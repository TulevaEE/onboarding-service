package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS;

import ee.tuleva.onboarding.analytics.AnalyticsLeaver;
import ee.tuleva.onboarding.analytics.AnalyticsLeaversRepository;
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

@Component
@RequiredArgsConstructor
@Profile("!dev")
@Slf4j
public class AutoEmailSender {

  private final Clock clock;
  private final AnalyticsLeaversRepository leaversRepository;
  private final MailchimpService mailchimpService;
  private final EmailPersistenceService emailPersistenceService;

  // once per month on the second working day of the month at 19:10
  // @Scheduled(cron = "0 10 19 2W * *", zone = "Europe/Tallinn")
  @Scheduled(cron = "0 10 17 * * *", zone = "Europe/Tallinn")
  public void sendMonthlyLeaverEmail() {
    LocalDate startDate = LocalDate.now(clock).withDayOfMonth(1);
    LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
    List<AnalyticsLeaver> leavers = leaversRepository.fetchLeavers(startDate, endDate);

    for (AnalyticsLeaver leaver : leavers) {
      if (leaver.lastEmailSentDate() != null
          && leaver.lastEmailSentDate().isAfter(LocalDateTime.now(clock).minusMonths(4))) {
        log.info("Already sent email to leaver, skipping {}", leaver.personalCode());
        continue;
      }
      log.info("Sending email to leaver {}", leaver.personalCode());
      mailchimpService.sendEvent(leaver.email(), "new_leaver");
      emailPersistenceService.save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
    }
  }
}
