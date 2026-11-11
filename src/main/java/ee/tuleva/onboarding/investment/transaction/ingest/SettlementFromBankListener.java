package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.event.RunSettlementFromBankRequested.JOB_NAME;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.RunSettlementFromBankRequested;
import ee.tuleva.onboarding.investment.event.trigger.JobTriggerQueue;
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

  @Value("${transaction-registry.settlement-check.scan-lookback-days:60}")
  private int scanLookbackDays = 60;

  @EventListener
  @Order(SebPriceVsNavCheckListener.LISTENER_ORDER + 10)
  public void onReportImportCompleted(ReportImportCompleted event) {
    if (event.provider() != SEB || event.reportType() != PENDING_TRANSACTIONS) {
      return;
    }
    try {
      if (!settlesUnderTheLock()) {
        defer("its lock is held");
      }
    } catch (SettlementFromBankLockLostException e) {
      log.warn("Settling registry orders from the bank lost its lock: trigger={}", JOB_NAME, e);
      defer("its lock was lost");
    } catch (RuntimeException e) {
      log.error("Settling registry orders from the bank failed: trigger={}", JOB_NAME, e);
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
    settlementFromBankService.settleOrdersBookedSince(since, stillHoldsTheLock);
  }

  private void defer(String reason) {
    log.debug(
        "Settling registry orders from the bank deferred: reason={}, trigger={}", reason, JOB_NAME);
    jobTriggerQueue.defer(JOB_NAME);
  }
}
