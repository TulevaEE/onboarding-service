package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.pipeline.PipelineStep.REPORT_EMAIL;
import static ee.tuleva.onboarding.pipeline.PipelineStep.REPORT_PERSIST;
import static ee.tuleva.onboarding.savings.fund.nav.NavPublication.NOT_SENT;
import static ee.tuleva.onboarding.savings.fund.nav.NavPublication.SENT;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavPublisher {

  private static final String NAV_ACCOUNT_TYPE = "NAV";

  private final SavingsFundPublicNav savingsFundPublicNav;
  private final NavReportMapper navReportMapper;
  private final NavReportRepository navReportRepository;
  private final NavReportEmailSender navReportEmailSender;
  private final NavNotifier navNotifier;
  private final OperationsNotificationService notificationService;
  private final NavPublicationGate trackingDifferenceGate;
  private final PipelineTracker pipelineTracker;
  private final TransactionOperations transactionOperations;

  public NavPublication publish(NavCalculationResult result) {
    savingsFundPublicNav.publish(result);

    UUID calculationId = UUID.randomUUID();
    Optional<NavReportRow> earlierUnsent = earlierUnsentUnitsRow(result);
    List<NavReportRow> reportRows = persistReportRows(result, calculationId);
    ReviewEmail reviewEmail = ReviewEmail.comparing(earlierUnsent, reportRows);

    NavPublication publication = deliver(result, reportRows, calculationId, reviewEmail);

    navNotifier.notify(result);

    log.info(
        "Published NAV: fund={}, date={}, navPerUnit={}, aum={}",
        result.fund(),
        result.calculationDate(),
        result.navPerUnit(),
        result.aum());
    return publication;
  }

  private NavPublication deliver(
      NavCalculationResult result,
      List<NavReportRow> reportRows,
      UUID calculationId,
      ReviewEmail reviewEmail) {
    if (reportRows.isEmpty()) {
      notifyEmptyReport(result);
      return NOT_SENT;
    }
    return sendReportEmailWithGate(result, reportRows, calculationId, reviewEmail);
  }

  private Optional<NavReportRow> earlierUnsentUnitsRow(NavCalculationResult result) {
    return navReportRepository.findLatestUnsentUnitsRow(
        result.fund().getCode(), result.positionReportDate());
  }

  void publishRevision(NavCalculationResult result, NavRevision revision) {
    UUID calculationId = UUID.randomUUID();
    Optional<NavReportRow> earlierUnsent = earlierUnsentUnitsRow(result);
    List<NavReportRow> reportRows = persistReportRows(result, calculationId);
    ReviewEmail reviewEmail = ReviewEmail.comparing(earlierUnsent, reportRows);

    if (reportRows.isEmpty()) {
      throw new IllegalStateException(
          "NAV revision rows not persisted: fund=%s, date=%s"
              .formatted(result.fund().getCode(), result.positionReportDate()));
    }

    String reason = revision.describe(result);
    routeToInternalReview(
        result,
        reportRows,
        reason,
        NavReviewMessages.revised(result, reason, reviewEmail),
        reviewEmail);

    log.info(
        "Published NAV revision: fund={}, date={}, navPerUnit={}, aum={}",
        result.fund(),
        result.positionReportDate(),
        result.navPerUnit(),
        result.aum());
  }

  private List<NavReportRow> persistReportRows(NavCalculationResult result, UUID calculationId) {
    pipelineTracker.stepStarted(REPORT_PERSIST);
    try {
      var rows = navReportMapper.map(result);
      rows.forEach(row -> row.setCalculationId(calculationId));
      navReportRepository.replaceByNavDateAndFundCode(
          result.positionReportDate(), result.fund().getCode(), rows);
      pipelineTracker.stepCompleted(REPORT_PERSIST);
      return rows;
    } catch (Exception e) {
      pipelineTracker.stepFailed(REPORT_PERSIST, e.getMessage());
      log.error(
          "Failed to persist NAV report: fund={}, calculationDate={}, positionReportDate={}",
          result.fund(),
          result.calculationDate(),
          result.positionReportDate(),
          e);
      return List.of();
    }
  }

  private void notifyEmptyReport(NavCalculationResult result) {
    log.error(
        "No report rows to publish, skipping email: fund={}, date={}",
        result.fund(),
        result.positionReportDate());
    notificationService.sendMessage(
        "NAV report has no rows: fund="
            + result.fund().getCode()
            + ", date="
            + result.positionReportDate(),
        SAVINGS);
  }

  private NavPublication sendReportEmailWithGate(
      NavCalculationResult result,
      List<NavReportRow> reportRows,
      UUID calculationId,
      ReviewEmail reviewEmail) {
    Optional<BigDecimal> navTheTrusteeHas =
        navReportRepository.findPublishedNavPerUnit(
            result.positionReportDate(), result.fund().getCode(), NAV_ACCOUNT_TYPE);
    if (navTheTrusteeHas.isPresent()) {
      String reason =
          "SEB already has a NAV for this date: sentNav=%s, recalculatedNav=%s"
              .formatted(
                  navTheTrusteeHas.get().toPlainString(), result.navPerUnit().toPlainString());
      Hold revision = new Hold(reason, NavReviewMessages.revised(result, reason, reviewEmail));
      return requireNonNull(
          transactionOperations.execute(
              transaction ->
                  isReleasedOrReplacedOnceTheDateIsLocked(result, calculationId)
                      ? NOT_SENT
                      : holdForReview(result, reportRows, revision, reviewEmail)));
    }
    Optional<Hold> hold =
        checkGates(result)
            .map(reason -> new Hold(reason, NavReviewMessages.held(result, reason, reviewEmail)))
            .or(
                () ->
                    savingsFundPublicNav
                        .differenceFrom(result)
                        .map(
                            reason ->
                                new Hold(
                                    reason,
                                    NavReviewMessages.publicNavChanged(
                                        result, reason, reviewEmail))));

    return requireNonNull(
        transactionOperations.execute(
            transaction -> {
              if (isOvertakenWhileTheChecksRan(result, calculationId)) {
                return NOT_SENT;
              }
              return hold.map(held -> holdForReview(result, reportRows, held, reviewEmail))
                  .orElseGet(() -> sendToSeb(result, reportRows, calculationId));
            }));
  }

  private record Hold(String reason, String actionMessage) {}

  private boolean isReleasedOrReplacedOnceTheDateIsLocked(
      NavCalculationResult result, UUID calculationId) {
    boolean overtaken =
        navReportRepository.isReleasedOrReplacedOnceTheDateIsLocked(
            result.fund().getCode(), result.positionReportDate(), calculationId);
    if (overtaken) {
      log.info(
          "Recalculation of a date SEB has not held for review, it was released or replaced meanwhile: fund={}, date={}, calculationId={}",
          result.fund(),
          result.positionReportDate(),
          calculationId);
    }
    return overtaken;
  }

  private boolean isOvertakenWhileTheChecksRan(NavCalculationResult result, UUID calculationId) {
    boolean overtaken =
        navReportRepository.isOvertakenOnceTheDateIsLocked(
            result.fund().getCode(), result.positionReportDate(), calculationId);
    if (overtaken) {
      log.info(
          "NAV neither sent nor held, a release or a newer calculation of the date overtook it while the checks ran: fund={}, date={}, calculationId={}",
          result.fund(),
          result.positionReportDate(),
          calculationId);
    }
    return overtaken;
  }

  private NavPublication holdForReview(
      NavCalculationResult result,
      List<NavReportRow> reportRows,
      Hold hold,
      ReviewEmail reviewEmail) {
    routeToInternalReview(result, reportRows, hold.reason(), hold.actionMessage(), reviewEmail);
    return NOT_SENT;
  }

  private NavPublication sendToSeb(
      NavCalculationResult result, List<NavReportRow> reportRows, UUID calculationId) {
    pipelineTracker.stepStarted(REPORT_EMAIL);
    if (sendEmail(reportRows, result)) {
      navReportRepository.markAsPublished(calculationId);
      pipelineTracker.stepCompleted(REPORT_EMAIL);
      return SENT;
    }
    pipelineTracker.stepFailed(REPORT_EMAIL, "Mandrill send failed");
    log.error(
        "NAV report email failed, rows remain unpublished: fund={}, date={}",
        result.fund(),
        result.positionReportDate());
    notificationService.sendMessage(
        "NAV report email failed: fund="
            + result.fund().getCode()
            + ", date="
            + result.positionReportDate(),
        SAVINGS);
    return NOT_SENT;
  }

  private void routeToInternalReview(
      NavCalculationResult result,
      List<NavReportRow> reportRows,
      String reason,
      String actionMessage,
      ReviewEmail reviewEmail) {
    log.error(
        "NAV report held for internal review (NOT sent to SEB): fund={}, date={}, reason={}",
        result.fund(),
        result.positionReportDate(),
        reason);
    pipelineTracker.stepStarted(REPORT_EMAIL);
    if (reviewEmail == ReviewEmail.ALREADY_SENT) {
      log.info(
          "NAV review email not sent again, the NAV and AUM are those already emailed: fund={}, date={}",
          result.fund(),
          result.positionReportDate());
      pipelineTracker.stepCompleted(REPORT_EMAIL);
      notificationService.sendMessage(actionMessage, SAVINGS);
      return;
    }
    if (navReportEmailSender.sendForReview(reportRows, result)) {
      pipelineTracker.stepCompleted(REPORT_EMAIL);
      notificationService.sendMessage(actionMessage, SAVINGS);
    } else {
      pipelineTracker.stepFailed(REPORT_EMAIL, "internal review email failed");
      log.error(
          "NAV review email failed, rows remain unpublished: fund={}, date={}",
          result.fund(),
          result.positionReportDate());
      notificationService.sendMessage(
          "NAV review email FAILED (rows unpublished): fund=%s, date=%s — %s"
              .formatted(result.fund().getCode(), result.positionReportDate(), reason),
          SAVINGS);
    }
  }

  private boolean sendEmail(List<NavReportRow> reportRows, NavCalculationResult result) {
    try {
      return navReportEmailSender.send(reportRows, result);
    } catch (Exception e) {
      log.error(
          "Failed to send NAV report email: fund={}, calculationDate={}, positionReportDate={}",
          result.fund(),
          result.calculationDate(),
          result.positionReportDate(),
          e);
      return false;
    }
  }

  private Optional<String> checkGates(NavCalculationResult result) {
    try {
      var tdResult = trackingDifferenceGate.check(result.fund(), result.positionReportDate());
      if (tdResult.isPresent()) {
        return tdResult;
      }
    } catch (Exception e) {
      log.warn(
          "TD gate error, proceeding with email: fund={}, date={}",
          result.fund(),
          result.positionReportDate(),
          e);
    }
    return Optional.empty();
  }
}
