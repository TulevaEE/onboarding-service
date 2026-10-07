package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!dev")
class AccountOpenedEmailJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final int OLDEST_OPENING_IN_DAYS = 3;
  private static final int MAX_RECIPIENTS = 100;

  private final Clock clock;
  private final OpenedAccountRepository repository;
  private final ChildAccountOpenedEmailSender childSender;
  private final AdultAccountOpenedEmailSender adultSender;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "AccountOpenedEmailJob_sendEmails",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void sendEmails() {
    var today = LocalDate.now(clock.withZone(TALLINN));
    var openedUntil = today.atStartOfDay(TALLINN).toInstant();
    var openedFrom = openedUntil.minus(OLDEST_OPENING_IN_DAYS, DAYS);
    var accounts =
        repository.fetch(openedFrom, openedUntil).stream()
            .filter(account -> canBeWelcomed(account, today))
            .toList();

    if (accounts.size() > MAX_RECIPIENTS) {
      log.error(
          "Too many savings fund account opened emails, skipping: accounts={}, maxRecipients={}, openedFrom={}, openedUntil={}",
          accounts.size(),
          MAX_RECIPIENTS,
          openedFrom,
          openedUntil);
      notificationService.sendMessage(
          "Savings fund account opened emails skipped: %d accounts is over the %d cap, nobody will be welcomed until someone looks"
              .formatted(accounts.size(), MAX_RECIPIENTS),
          SAVINGS,
          ERROR);
      return;
    }

    log.info("Sending savings fund account opened emails: accounts={}", accounts.size());
    accounts.forEach(account -> welcome(account, today));
  }

  private void welcome(OpenedAccount account, LocalDate today) {
    try {
      if (isMinor(account, today)) {
        childSender.send(account);
      } else {
        adultSender.send(account);
      }
    } catch (Exception e) {
      log.error("Failed to send a savings fund account opened email", e);
    }
  }

  private static boolean canBeWelcomed(OpenedAccount account, LocalDate today) {
    if (isMinor(account, today)) {
      return account.represented();
    }
    var email = account.email();
    return email != null && !email.isBlank() && !account.represented();
  }

  private static boolean isMinor(OpenedAccount account, LocalDate today) {
    try {
      return PersonalCode.isMinor(account.code(), today);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
