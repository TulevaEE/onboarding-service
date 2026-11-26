package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.pipeline.PipelineStep.REPORT_EMAIL;
import static ee.tuleva.onboarding.pipeline.PipelineStep.REPORT_PERSIST;
import static ee.tuleva.onboarding.savings.fund.nav.NavPublication.NOT_SENT;
import static ee.tuleva.onboarding.savings.fund.nav.NavPublication.SENT;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueWriter;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  private static final String NAV_PROVIDER = "TULEVA";
  private static final String NAV_ACCOUNT_TYPE = "NAV";

  private final FundValueWriter fundValueWriter;
  private final FundValueQueries fundValueQueries;
  private final NavReportMapper navReportMapper;
  private final NavReportRepository navReportRepository;
  private final NavReportEmailSender navReportEmailSender;
  private final NavNotifier navNotifier;
  private final OperationsNotificationService notificationService;
  private final NavPublicationGate trackingDifferenceGate;
  private final PipelineTracker pipelineTracker;
  private final TransactionOperations transactionOperations;

  public NavPublication publish(NavCalculationResult result) {
    publishToFundValueApi(result);

    UUID calculationId = UUID.randomUUID();
    List<NavReportRow> reportRows = persistReportRows(result, calculationId);

    NavPublication publication = deliver(result, reportRows, calculationId);

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
      NavCalculationResult result, List<NavReportRow> reportRows, UUID calculationId) {
    if (reportRows.isEmpty()) {
      notifyEmptyReport(result);
      return NOT_SENT;
    }
    return sendReportEmailWithGate(result, reportRows, calculationId);
  }

  void publishRevision(NavCalculationResult result, NavRevision revision) {
    UUID calculationId = UUID.randomUUID();
    List<NavReportRow> reportRows = persistReportRows(result, calculationId);

    if (reportRows.isEmpty()) {
      throw new IllegalStateException(
          "NAV revision rows not persisted: fund=%s, date=%s"
              .formatted(result.fund().getCode(), result.positionReportDate()));
    }

    String reason = revision.describe(result);
    routeToInternalReview(result, reportRows, reason, revisionActionMessage(result, reason));

    log.info(
        "Published NAV revision: fund={}, date={}, navPerUnit={}, aum={}",
        result.fund(),
        result.positionReportDate(),
        result.navPerUnit(),
        result.aum());
  }

  private void publishToFundValueApi(NavCalculationResult result) {
    if (result.fund().isSavingsFund()) {
      publishNav(result);
      publishAum(result);
    }
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
      NavCalculationResult result, List<NavReportRow> reportRows, UUID calculationId) {
    Optional<BigDecimal> navTheTrusteeHas =
        navReportRepository.findPublishedNavPerUnit(
            result.positionReportDate(), result.fund().getCode(), NAV_ACCOUNT_TYPE);
    if (navTheTrusteeHas.isPresent()) {
      String reason =
          "SEB already has a NAV for this date: sentNav=%s, recalculatedNav=%s"
              .formatted(
                  navTheTrusteeHas.get().toPlainString(), result.navPerUnit().toPlainString());
      routeToInternalReview(result, reportRows, reason, revisionActionMessage(result, reason));
      return NOT_SENT;
    }
    Optional<String> gateFailure = checkGates(result);
    if (gateFailure.isPresent()) {
      routeToInternalReview(
          result, reportRows, gateFailure.get(), reviewActionMessage(result, gateFailure.get()));
      return NOT_SENT;
    }
    Optional<String> publicNavDifference = publicNavDifference(result);
    if (publicNavDifference.isPresent()) {
      routeToInternalReview(
          result,
          reportRows,
          publicNavDifference.get(),
          publicNavActionMessage(result, publicNavDifference.get()));
      return NOT_SENT;
    }

    return requireNonNull(
        transactionOperations.execute(
            transaction -> sendToSebUnlessOvertaken(result, reportRows, calculationId)));
  }

  private NavPublication sendToSebUnlessOvertaken(
      NavCalculationResult result, List<NavReportRow> reportRows, UUID calculationId) {
    String fundCode = result.fund().getCode();
    LocalDate navDate = result.positionReportDate();
    navReportRepository.findTheDatesRowsForUpdate(fundCode, navDate);
    if (navReportRepository.existsPublishedByNavDateAndFundCode(navDate, fundCode)
        || isReplacedByANewerCalculation(fundCode, navDate, calculationId)) {
      log.info(
          "NAV not sent, a release or a newer calculation of the date overtook it while the gate ran: fund={}, date={}, calculationId={}",
          fundCode,
          navDate,
          calculationId);
      return NOT_SENT;
    }

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

  private Optional<String> publicNavDifference(NavCalculationResult result) {
    if (!result.fund().isSavingsFund()) {
      return Optional.empty();
    }
    LocalDate navDate = result.positionReportDate();
    Optional<BigDecimal> publicNav = publicValue(result.fund().getIsin(), navDate);
    Optional<BigDecimal> publicAum = publicValue(result.fund().getAumKey(), navDate);
    if (!differs(publicNav, result.navPerUnit()) && !differs(publicAum, result.aum())) {
      return Optional.empty();
    }
    return Optional.of(
        "The public NAV in index_values differs from the recalculation: publicNav=%s, recalculatedNav=%s, publicAum=%s, recalculatedAum=%s"
            .formatted(
                plain(publicNav),
                result.navPerUnit().toPlainString(),
                plain(publicAum),
                result.aum().toPlainString()));
  }

  private Optional<BigDecimal> publicValue(String key, LocalDate navDate) {
    return fundValueQueries.getValueForDate(key, navDate).map(FundValue::value);
  }

  private static boolean differs(Optional<BigDecimal> publicValue, BigDecimal recalculated) {
    return publicValue.filter(value -> value.compareTo(recalculated) != 0).isPresent();
  }

  private static String plain(Optional<BigDecimal> value) {
    return value.map(BigDecimal::toPlainString).orElse("not stored");
  }

  private boolean isReplacedByANewerCalculation(
      String fundCode, LocalDate navDate, UUID calculationId) {
    return navReportRepository
        .findLatestCalculationId(fundCode, navDate)
        .filter(latest -> !latest.equals(calculationId))
        .isPresent();
  }

  private void routeToInternalReview(
      NavCalculationResult result,
      List<NavReportRow> reportRows,
      String reason,
      String actionMessage) {
    log.error(
        "NAV report held for internal review (NOT sent to SEB): fund={}, date={}, reason={}",
        result.fund(),
        result.positionReportDate(),
        reason);
    pipelineTracker.stepStarted(REPORT_EMAIL);
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

  private String reviewActionMessage(NavCalculationResult result, String reason) {
    return """
        🔴 NAV report HELD for review — NOT sent to SEB.
        %s
        The CSV report was emailed to funds@tuleva.ee.
        Until it is released or passes the check, the NAV is recalculated every 5–15 minutes and this message repeats.

        What to do now:
        1. Verify the NAV in the "%s NAV arvutamine ..." email (funds@tuleva.ee) reconciles with the SEB custodian report.
        2. If correct, release it. The release sends the report to SEB; do not forward the email by hand.
        %s
        3. Optional (needs compliance sign-off) — widen the TD gate going forward:
           INSERT INTO investment_parameter (parameter_name, fund_code, effective_date, numeric_value) VALUES ('TRACKING_BREACH_THRESHOLD', NULL, CURRENT_DATE, 0.005);"""
        .formatted(reason, result.fund().getCode(), howToRelease(result));
  }

  private String publicNavActionMessage(NavCalculationResult result, String reason) {
    return """
        🔴 %s NAV recalculated to a value other than the public one — held for review, NOT sent to SEB.
        %s
        The CSV report was emailed to funds@tuleva.ee.
        Issuing and redemptions price at the public NAV in index_values, which is append-only.
        Until it is corrected or released, the NAV is recalculated every 5–15 minutes and this message repeats.

        What to do now:
        1. Check the recalculated NAV against the SEB custodian report.
        2. If it is correct, correct the NAV and AUM of navDate=%s in index_values by hand. The next recalculation then goes to SEB on its own.
        3. Or release it as it is. The release sends the report to SEB and leaves index_values unchanged.
        %s"""
        .formatted(
            result.fund().getCode(), reason, result.positionReportDate(), howToRelease(result));
  }

  private String revisionActionMessage(NavCalculationResult result, String reason) {
    String fundCode = result.fund().getCode();
    String savingsFundStep =
        result.fund().isSavingsFund()
            ? "\n4. %s only: the public NAV in index_values is append-only and still holds the earlier value; correct it manually."
                .formatted(fundCode)
            : "";
    return """
        🔴 NAV REVISED after the custodian position report changed — held for review, NOT sent to SEB.
        fund=%s, navDate=%s: %s
        The revised CSV report was emailed to funds@tuleva.ee.

        What to do now:
        1. Check the changed rows against the new SEB position report.
        2. If correct, release it. The release sends the revised report to SEB; do not forward the email by hand.
        %s
        3. Until it is released, onboarding-service reads only a NAV SEB has received.%s"""
        .formatted(
            fundCode, result.positionReportDate(), reason, howToRelease(result), savingsFundStep);
  }

  private static String howToRelease(NavCalculationResult result) {
    String fundCode = result.fund().getCode();
    LocalDate navDate = result.positionReportDate();
    return ("   agent-gateway nav_release (two ✅): fund=%s, navDate=%s\n"
            + "   Until agent-gateway has nav_release: GET /admin/nav/%s/%s shows the values to"
            + " approve; POST navPerUnit, aum and approvedBy to /admin/nav/%s/%s/release")
        .formatted(fundCode, navDate, fundCode, navDate, fundCode, navDate);
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

  private void publishNav(NavCalculationResult result) {
    FundValue navValue =
        new FundValue(
            result.fund().getIsin(),
            result.positionReportDate(),
            result.navPerUnit(),
            NAV_PROVIDER,
            result.calculatedAt());

    fundValueWriter.save(navValue);
  }

  private void publishAum(NavCalculationResult result) {
    FundValue aumValue =
        new FundValue(
            result.fund().getAumKey(),
            result.positionReportDate(),
            result.aum(),
            NAV_PROVIDER,
            result.calculatedAt());

    fundValueWriter.save(aumValue);
  }
}
