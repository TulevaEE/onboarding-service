package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.skippedCheckReason;

import ee.tuleva.onboarding.investment.event.trigger.JobTriggerLease;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
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
  private final TransactionAuditEventRepository auditEventRepository;
  private final SebReplayWindow replayWindow;
  private final JobTriggerLease jobTriggerLease;

  ReplaySummary replayOldestFirst(
      LocalDate windowStart, LocalDate firstReportDate, BooleanSupplier stillHoldsTheLock) {
    Instant stopBy = stopBy();
    SebReplayListings listings = new SebReplayListings();
    Optional<StopReason> stoppedReadingHistory =
        readListingsBefore(windowStart, firstReportDate, listings, stopBy, stillHoldsTheLock);
    if (stoppedReadingHistory.isPresent()) {
      return new ReplaySummary(
          List.of(),
          0,
          0,
          0,
          List.of(),
          List.of(),
          Optional.of(new Stop(firstReportDate, stoppedReadingHistory.get())));
    }
    long executionsBefore = executionRepository.count();
    long settlementsBefore = settlementRepository.count();
    long redatedBefore = redatedSettlements();
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
                reconciliationService.replay(
                    report.get(), reportAlerts::add, listings, stillHoldsTheLock));
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
        redatedSettlements() - redatedBefore,
        List.copyOf(heldBackAlerts),
        List.copyOf(skippedChecks),
        stop);
  }

  private long redatedSettlements() {
    return auditEventRepository.countByEventType(ReconciliationAuditRecorder.SETTLEMENT_REDATED);
  }

  private Instant stopBy() {
    Instant ownBudgetEnds = SebReplayWindow.stopByForAReplayStartedAt(clock.instant());
    return jobTriggerLease
        .deadline()
        .filter(leaseDeadline -> leaseDeadline.isBefore(ownBudgetEnds))
        .orElse(ownBudgetEnds);
  }

  private Optional<StopReason> readListingsBefore(
      LocalDate windowStart,
      LocalDate firstReportDate,
      SebReplayListings listings,
      Instant stopBy,
      BooleanSupplier stillHoldsTheLock) {
    for (LocalDate date : windowStart.datesUntil(firstReportDate).toList()) {
      Optional<StopReason> mustStop = mustStopBeforeTheNextReport(stopBy, stillHoldsTheLock);
      if (mustStop.isPresent()) {
        log.warn(
            "SEB pending transactions replay stopped while reading earlier listings: date={},"
                + " reason={}",
            date,
            mustStop.get());
        return mustStop;
      }
      reportService
          .getReport(SEB, PENDING_TRANSACTIONS, date)
          .ifPresent(report -> reconciliationService.noteListedBy(report, listings));
    }
    log.info(
        "SEB pending transactions replay read what the earlier reports listed: windowStart={},"
            + " firstReportDate={}, brokerRefs={}",
        windowStart,
        firstReportDate,
        listings.size());
    return Optional.empty();
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
