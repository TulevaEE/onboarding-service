package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.JobRunSchedule.R16_FLOW_RECALC;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.event.RunR16FlowRecalcRequested;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class R16FlowRecalcJob {

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final R16StatusService statusService;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = R16_FLOW_RECALC, zone = TIMEZONE)
  @SchedulerLock(name = "R16FlowRecalcJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping R16 flow recalculation on non-working day: today={}", today);
      return;
    }
    List<R16FundStatus> statusesWithData =
        statusService.status().stream().filter(status -> status.totalOutflowEur() != null).toList();
    if (statusesWithData.isEmpty()) {
      log.info("No funds with R16 data to recalculate: today={}", today);
      return;
    }
    log.info(
        "Recalculated R16 flows: today={}, funds={}",
        today,
        statusesWithData.stream().map(status -> status.fund().getCode()).toList());
    notificationService.sendMessage(summaryMessage(today, statusesWithData), INVESTMENT);
  }

  @EventListener(classes = RunR16FlowRecalcRequested.class)
  void onR16FlowRecalcRequested() {
    run();
  }

  private static String summaryMessage(LocalDate today, List<R16FundStatus> statuses) {
    StringBuilder message =
        new StringBuilder("📤 R16 prognoositud pensionimaksed – %s".formatted(today));
    for (R16FundStatus status : statuses) {
      message.append(
          "\n%s: faas %s, väljamaks %s €, tähtaeg %s, müügi tähtaeg %s%s"
              .formatted(
                  status.fund().getCode(),
                  status.phase(),
                  status.totalOutflowEur(),
                  status.paymentDeadline(),
                  status.sellByDate(),
                  status.suppressedByR45() ? " (asendatud R45-ga)" : ""));
    }
    return message.toString();
  }
}
