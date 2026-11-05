package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.skippedCheckReason;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.ReplaySummary;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.SkippedCheck;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.Stop;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.StopReason;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class SebPendingReportReplayer {

  private final Clock clock;
  private final InvestmentReportService reportService;
  private final SebPendingTransactionReconciliationService reconciliationService;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementRepository settlementRepository;
  private final SebReplayWindow replayWindow;

  ReplaySummary replayOldestFirst(LocalDate firstReportDate, BooleanSupplier stillHoldsTheLock) {
    Instant stopBy = SebReplayWindow.stopByForAReplayStartedAt(clock.instant());
    long executionsBefore = executionRepository.count();
    long settlementsBefore = settlementRepository.count();
    List<ReconciliationAlert> heldBackAlerts = new ArrayList<>();
    List<SkippedCheck> skippedChecks = new ArrayList<>();
    List<LocalDate> replayed = new ArrayList<>();
    Optional<Stop> stop = Optional.empty();
    for (LocalDate date : firstReportDate.datesUntil(LocalDate.now(clock).plusDays(1)).toList()) {
      List<ReconciliationAlert> reportAlerts = new ArrayList<>();
      Optional<String> skippedCheck;
      try {
        Optional<InvestmentReport> report =
            reportService.getReport(SEB, PENDING_TRANSACTIONS, date);
        if (report.isEmpty()) {
          continue;
        }
        Optional<StopReason> mustStop = mustStopBeforeTheNextReport(stopBy, stillHoldsTheLock);
        if (mustStop.isPresent()) {
          log.warn(
              "SEB pending transactions replay stopped before a report: reportDate={}, reason={}",
              date,
              mustStop.get());
          stop = Optional.of(new Stop(date, mustStop.get()));
          break;
        }
        skippedCheck =
            skippedCheckReason(
                reconciliationService.replay(report.get(), reportAlerts::add, stillHoldsTheLock));
      } catch (SebReconciliationLockLostException e) {
        log.error("SEB pending transactions replay lost its lock: reportDate={}", date, e);
        stop = Optional.of(new Stop(date, StopReason.LOCK_LOST));
        break;
      } catch (RuntimeException e) {
        log.error("SEB pending transactions replay stopped: reportDate={}", date, e);
        stop = Optional.of(new Stop(date, StopReason.REPORT_FAILED));
        break;
      }
      reportAlerts.forEach(
          alert -> log.info("SEB pending transactions replay held back alert: alert={}", alert));
      heldBackAlerts.addAll(reportAlerts);
      skippedCheck.ifPresent(reason -> skippedChecks.add(new SkippedCheck(date, reason)));
      replayed.add(date);
    }
    return new ReplaySummary(
        List.copyOf(replayed),
        executionRepository.count() - executionsBefore,
        settlementRepository.count() - settlementsBefore,
        List.copyOf(heldBackAlerts),
        List.copyOf(skippedChecks),
        stop);
  }

  private Optional<StopReason> mustStopBeforeTheNextReport(
      Instant stopBy, BooleanSupplier stillHoldsTheLock) {
    Instant now = clock.instant();
    if (!replayWindow.isOpenAt(now)) {
      return Optional.of(StopReason.IMPORT_WINDOW_OPENS);
    }
    if (!now.isBefore(stopBy)) {
      return Optional.of(StopReason.TIME_BUDGET_SPENT);
    }
    return stillHoldsTheLock.getAsBoolean() ? Optional.empty() : Optional.of(StopReason.LOCK_LOST);
  }
}
