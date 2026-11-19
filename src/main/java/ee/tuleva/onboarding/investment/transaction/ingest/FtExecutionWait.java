package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.MissingExecution.Stage.AWAITING_SEB_REPORT;
import static ee.tuleva.onboarding.investment.transaction.ingest.MissingExecution.Stage.MISSING_AFTER_SEB_REPORT;
import static ee.tuleva.onboarding.investment.transaction.ingest.MissingExecution.Stage.SEB_REPORT_NOT_RECONCILED;

import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class FtExecutionWait {

  private static final int SEB_REPORT_FOR_THE_DAY_AFTER_THE_TRADE = 1;
  private static final int SEB_REPORT_OVERDUE_AFTER_BUSINESS_DAYS = 2;

  private final ReconciliationAuditRecorder reconciliationRecorder;
  private final Target2Calendar target2Calendar;
  private final Clock clock;

  MissingExecution assess(LocalDate tradeDate) {
    LocalDate firstReportAsOf =
        target2Calendar.addBusinessDays(tradeDate, SEB_REPORT_FOR_THE_DAY_AFTER_THE_TRADE);
    LocalDate waitUntil =
        target2Calendar.addBusinessDays(tradeDate, SEB_REPORT_OVERDUE_AFTER_BUSINESS_DAYS);
    return reconciliationRecorder
        .firstReportReconciledAsOfOrAfter(firstReportAsOf)
        .map(FtExecutionWait::missingAfter)
        .orElseGet(() -> withoutReconciledReport(firstReportAsOf, waitUntil));
  }

  private static MissingExecution missingAfter(LocalDate reportAsOf) {
    return new MissingExecution(
        MISSING_AFTER_SEB_REPORT,
        Map.of(
            "executionMissing",
            "no execution after SEB pending report as of " + reportAsOf,
            "sebReportAsOf",
            reportAsOf.toString()));
  }

  private MissingExecution withoutReconciledReport(LocalDate firstReportAsOf, LocalDate waitUntil) {
    if (LocalDate.now(clock).isAfter(waitUntil)) {
      return new MissingExecution(
          SEB_REPORT_NOT_RECONCILED,
          Map.of(
              "executionMissing",
              "no SEB pending report as of "
                  + firstReportAsOf
                  + " or later reconciled by "
                  + waitUntil,
              "sebReportMissingAsOf",
              firstReportAsOf.toString()));
    }
    return new MissingExecution(
        AWAITING_SEB_REPORT,
        Map.of(
            "awaitingSebReportAsOf", firstReportAsOf.toString(),
            "executionPendingUntil", waitUntil.toString()));
  }
}
