package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.JobRunSchedule.MODEL_REBALANCE_CHECK;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.Arrays.stream;

import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("production")
class ModelRebalanceCheckJob {

  private final RebalanceWindows rebalanceWindows;
  private final RebalanceAnnouncements announcements;
  private final OperationsNotificationService notificationService;
  private final EstonianCalendar estonianCalendar;
  private final Clock clock;

  @Scheduled(cron = MODEL_REBALANCE_CHECK, zone = TIMEZONE)
  @SchedulerLock(name = "ModelRebalanceCheckJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  void checkRebalanceWindows() {
    var today = LocalDate.now(clock);
    if (!estonianCalendar.isBusinessDay(today)) {
      return;
    }
    stream(TulevaFund.values()).forEach(fund -> check(fund, today));
  }

  private void check(TulevaFund fund, LocalDate today) {
    try {
      var windows =
          rebalanceWindows.of(
              fund, today, modelEffectiveDate -> announcements.closeOwed(fund, modelEffectiveDate));
      windows.current().ifPresent(ModelRebalanceCheckJob::logWindow);
      windows.superseded().forEach(this::closeSuperseded);
      windows.current().filter(announcements::isOwed).ifPresent(this::announce);
    } catch (RuntimeException e) {
      log.error("Model rebalance check could not run: fund={}, checkDate={}", fund, today, e);
      post(fund, RebalanceMessage.couldNotRun(fund));
    }
  }

  private void closeSuperseded(SupersededWindow superseded) {
    var window = superseded.window();
    if (post(window.fund(), RebalanceMessage.ofSuperseded(superseded))) {
      announcements.recordClose(window, clock.instant());
    }
  }

  private void announce(RebalanceWindow window) {
    postThenRecordSoNoAnnouncementIsLost(window, RebalanceMessage.of(window));
  }

  private void postThenRecordSoNoAnnouncementIsLost(
      RebalanceWindow window, RebalanceMessage message) {
    if (!post(window.fund(), message)) {
      return;
    }
    if (window.closed()) {
      announcements.recordClose(window, clock.instant());
      return;
    }
    announcements.recordOpen(window, clock.instant());
  }

  private boolean post(TulevaFund fund, RebalanceMessage message) {
    try {
      notificationService.sendMessage(message.text(), INVESTMENT, message.severity());
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Model rebalance check notification failed: fund={}, severity={}",
          fund,
          message.severity(),
          e);
      return false;
    }
  }

  private static void logWindow(RebalanceWindow window) {
    log.info(
        "Model rebalance window: fund={}, modelEffectiveDate={}, checkDate={}, state={},"
            + " closedOn={}, latestNavDate={}, deadline={}",
        window.fund(),
        window.modelEffectiveDate(),
        window.checkDate(),
        window.state(),
        window.closedOn(),
        window.latestNavDate(),
        window.deadline());
  }
}
