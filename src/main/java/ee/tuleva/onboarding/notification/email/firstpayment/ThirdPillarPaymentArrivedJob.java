package ee.tuleva.onboarding.notification.email.firstpayment;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
@Slf4j
public class ThirdPillarPaymentArrivedJob {

  private final FirstPaymentAudience firstPaymentAudience;
  private final ThirdPillarPaymentArrivedEmailService emailService;
  private final Clock clock;
  private final boolean dryRun;
  private final int maxRecipients;
  private final LocalDate historyFloor;
  private final int windowDays;

  public ThirdPillarPaymentArrivedJob(
      FirstPaymentAudience firstPaymentAudience,
      ThirdPillarPaymentArrivedEmailService emailService,
      Clock clock,
      @Value("${third-pillar-payment-arrived.dry-run:true}") boolean dryRun,
      @Value("${third-pillar-payment-arrived.max-recipients:200}") int maxRecipients,
      @Value("${third-pillar-payment-arrived.history-floor:2022-01-01}") LocalDate historyFloor,
      @Value("${third-pillar-payment-arrived.window-days:30}") int windowDays) {
    this.firstPaymentAudience = firstPaymentAudience;
    this.emailService = emailService;
    this.clock = clock;
    this.dryRun = dryRun;
    this.maxRecipients = maxRecipients;
    this.historyFloor = historyFloor;
    this.windowDays = windowDays;
  }

  @Scheduled(cron = "${third-pillar-payment-arrived.cron:0 0 12 * * TUE}", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ThirdPillarPaymentArrivedJob_run",
      lockAtMostFor = "23h",
      lockAtLeastFor = "10m")
  public void run() {
    Optional<LocalDate> oldestPayment = firstPaymentAudience.oldestOwnPaymentDate();
    if (oldestPayment.isEmpty() || oldestPayment.get().isAfter(historyFloor)) {
      log.error(
          "Transaction history too shallow to trust first-payment detection, refusing to run:"
              + " oldestPayment={}, historyFloor={}",
          oldestPayment.orElse(null),
          historyFloor);
      return;
    }

    LocalDate windowStart = LocalDate.now(clock).minusDays(windowDays);
    LocalDate adultBirthDateCutoff = LocalDate.now(clock).minusYears(18);
    List<FirstThirdPillarPayment> audience =
        firstPaymentAudience.fetchUnemailedFirstPayments(windowStart, adultBirthDateCutoff);

    if (audience.size() > maxRecipients) {
      log.error(
          "Too many first payment email recipients, refusing to run: audience={}, maxRecipients={}",
          audience.size(),
          maxRecipients);
      return;
    }

    if (dryRun) {
      log.info(
          "Dry run, not sending payment arrived emails: audience={}, windowStart={}",
          audience.size(),
          windowStart);
      return;
    }

    int sent = 0;
    int failed = 0;
    for (FirstThirdPillarPayment payment : audience) {
      try {
        if (emailService.send(payment)) {
          sent++;
        } else {
          failed++;
        }
      } catch (Exception e) {
        failed++;
        log.error(
            "Failed to process payment arrived email: personalCode={}", payment.personalCode(), e);
      }
    }
    log.info(
        "Payment arrived emails processed: audience={}, sent={}, skippedOrFailed={}",
        audience.size(),
        sent,
        failed);
  }
}
