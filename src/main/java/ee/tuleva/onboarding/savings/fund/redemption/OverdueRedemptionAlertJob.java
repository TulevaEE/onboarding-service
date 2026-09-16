package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.TALLINN;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
public class OverdueRedemptionAlertJob {

  private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final Duration VERIFICATION_GRACE = Duration.ofMinutes(15);
  private static final List<RedemptionRequest.Status> OPEN_STATUSES =
      List.of(RESERVED, IN_REVIEW, VERIFIED, REDEEMED, FAILED);

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final SavingFundDeadlinesService deadlinesService;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 0 9,15 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "OverdueRedemptionAlertJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void alertOverdueRedemptions() {
    Instant now = clock.instant();
    LocalDate today = now.atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }
    List<RedemptionRequest> overdue =
        redemptionRequestRepository.findByStatusIn(OPEN_STATUSES).stream()
            .filter(request -> isOverdue(request, now))
            .toList();
    if (overdue.isEmpty()) {
      return;
    }
    String message =
        "AML: %d redemption request(s) waiting past their deadline:\n%s"
            .formatted(
                overdue.size(),
                overdue.stream().map(request -> describe(request, today)).collect(joining("\n")));
    log.warn(message);
    try {
      notificationService.sendMessage(message, AML, ERROR);
    } catch (RuntimeException e) {
      log.error("Failed to send overdue redemption alert", e);
    }
  }

  private boolean isOverdue(RedemptionRequest request, Instant now) {
    return switch (request.getStatus()) {
      case RESERVED -> now.isAfter(request.getRequestedAt().plus(VERIFICATION_GRACE));
      case IN_REVIEW -> !now.isBefore(deadlinesService.getScreeningRetryDeadline(request));
      case VERIFIED -> now.isAfter(deadlinesService.getFulfillmentDeadline(request));
      case REDEEMED -> now.isAfter(settlementDeadline(request));
      case FAILED -> true;
      case CANCELLED, PROCESSED -> false;
    };
  }

  private Instant settlementDeadline(RedemptionRequest request) {
    ZonedDateTime payout = deadlinesService.getFulfillmentDeadline(request).atZone(TALLINN);
    return publicHolidays
        .addWorkingDays(payout.toLocalDate(), 1)
        .atTime(payout.toLocalTime())
        .atZone(TALLINN)
        .toInstant();
  }

  private String describe(RedemptionRequest request, LocalDate today) {
    String line =
        "id=%s, status=%s, amount=%s EUR, requested=%s, decisionCutoff=%s, workingDaysWaiting=%d"
            .formatted(
                request.getId(),
                request.getStatus(),
                request.getRequestedAmount().toPlainString(),
                minute(request.getRequestedAt()),
                minute(deadlinesService.getCancellationDeadline(request)),
                publicHolidays.countWorkingDaysBehind(
                    request.getRequestedAt().atZone(TALLINN).toLocalDate(), today));
    return switch (request.getStatus()) {
      case IN_REVIEW -> line + ", reason=" + request.getHoldReason();
      case FAILED -> line + ", error=" + request.getErrorReason();
      default -> line;
    };
  }

  private static String minute(Instant instant) {
    return MINUTE.format(instant.atZone(TALLINN));
  }
}
