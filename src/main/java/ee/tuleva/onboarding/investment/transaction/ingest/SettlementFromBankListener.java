package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.event.RunSettlementFromBankRequested.JOB_NAME;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.RunSettlementFromBankRequested;
import ee.tuleva.onboarding.investment.event.trigger.JobTriggerQueue;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SettlementFromBankListener {

  private final Clock clock;
  private final SettlementFromBankService settlementFromBankService;
  private final SebReconciliationLock reconciliationLock;
  private final JobTriggerQueue jobTriggerQueue;
  private final OperationsNotificationService notificationService;

  @Value("${transaction-registry.settlement-check.scan-lookback-days:60}")
  private int scanLookbackDays = 60;

  @EventListener
  @Order(SebPriceVsNavCheckListener.LISTENER_ORDER + 10)
  public void onReportImportCompleted(ReportImportCompleted event) {
    if (event.provider() != SEB || event.reportType() != PENDING_TRANSACTIONS) {
      return;
    }
    try {
      if (!settlesUnderTheLockOrLosesIt()) {
        log.debug(
            "Settling registry orders from the bank deferred, its lock is held or was lost:"
                + " trigger={}",
            JOB_NAME);
        jobTriggerQueue.defer(JOB_NAME);
      }
    } catch (RuntimeException e) {
      log.error("Settling registry orders from the bank failed: trigger={}", JOB_NAME, e);
    }
  }

  private boolean settlesUnderTheLockOrLosesIt() {
    try {
      return settlesUnderTheLock();
    } catch (SettlementFromBankLockLostException e) {
      log.warn("Settling registry orders from the bank lost its lock: trigger={}", JOB_NAME, e);
      return false;
    }
  }

  @EventListener(RunSettlementFromBankRequested.class)
  void onSettlementFromBankRequested() {
    if (!settlesUnderTheLock()) {
      log.debug(
          "Settling registry orders from the bank still waits for its lock: lock={}",
          SebReconciliationLock.NAME);
      throw new SebReconciliationAlreadyRunningException(SebReconciliationLock.NAME);
    }
  }

  private boolean settlesUnderTheLock() {
    return reconciliationLock.runIfFree(this::settleTheLookback);
  }

  private void settleTheLookback(BooleanSupplier stillHoldsTheLock) {
    LocalDate since = LocalDate.now(clock).minusDays(scanLookbackDays);
    settlementFromBankService
        .settleOrdersBookedSince(since, stillHoldsTheLock)
        .ifPresentOrElse(
            run -> report(since, run),
            () ->
                log.warn(
                    "Settled no registry orders from the bank, no usable SEB pending report:"
                        + " since={}",
                    since));
  }

  private void report(LocalDate since, BankSettlementRun run) {
    if (run.failed() == 0) {
      log.info("Settled registry orders from the bank: since={}, run={}", since, run);
      return;
    }
    log.warn("Settled registry orders from the bank, some failed: since={}, run={}", since, run);
    try {
      notificationService.sendMessage(failedMessage(run), INVESTMENT, WARNING);
    } catch (RuntimeException e) {
      log.error(
          "Settling registry orders from the bank could not post its warning: failed={}",
          run.failed(),
          e);
    }
  }

  private String failedMessage(BankSettlementRun run) {
    return "Registri tellimuste arveldamine panga kuupäevadega: "
        + run.failed()
        + " tellimuse arveldamine ebaõnnestus (põhjus on logis: \"Settling an order from the bank"
        + " failed\").\n"
        + "Panga kuupäeva said "
        + run.datedByTheBank()
        + ", pangakuupäevata jäi "
        + run.withoutBankDate()
        + ".\n"
        + "Teade kordub iga SEB ootel-tehingute raporti järel, kuni põhjus on parandatud"
        + " või tellimus on vanem kui "
        + scanLookbackDays
        + " päeva.";
  }
}
