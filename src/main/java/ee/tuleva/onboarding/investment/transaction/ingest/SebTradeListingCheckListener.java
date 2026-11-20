package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.RunSebTradeListingCheckRequested;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class SebTradeListingCheckListener {

  static final int LISTENER_ORDER = SebPriceVsNavCheckListener.LISTENER_ORDER + 20;
  private static final Duration BANK_MESSAGE_RERUN_INTERVAL = Duration.ofMinutes(30);

  private final Clock clock;
  private final SebTradeListingCheck check;
  private final SebReconciliationLock reconciliationLock;
  private final AtomicReference<Instant> lastBankMessageRun = new AtomicReference<>(Instant.MIN);

  SebTradeListingCheckListener(
      Clock clock, SebTradeListingCheck check, SebReconciliationLock reconciliationLock) {
    this.clock = clock;
    this.check = check;
    this.reconciliationLock = reconciliationLock;
  }

  @EventListener
  @Order(LISTENER_ORDER)
  public void onReportImportCompleted(ReportImportCompleted event) {
    if (event.provider() != SEB || event.reportType() != PENDING_TRANSACTIONS) {
      return;
    }
    runUnderTheLock("pending import");
  }

  @EventListener(BankMessagesProcessingCompleted.class)
  void onBankMessagesProcessed() {
    Instant now = clock.instant();
    Instant last = lastBankMessageRun.get();
    if (now.isBefore(last.plus(BANK_MESSAGE_RERUN_INTERVAL))
        || !lastBankMessageRun.compareAndSet(last, now)) {
      return;
    }
    runUnderTheLock("bank messages processed");
  }

  @EventListener(RunSebTradeListingCheckRequested.class)
  void onSebTradeListingCheckRequested() {
    if (!reconciliationLock.runIfFree(stillHoldsTheLock -> check.run())) {
      throw new SebReconciliationAlreadyRunningException(SebReconciliationLock.NAME);
    }
  }

  private void runUnderTheLock(String trigger) {
    try {
      if (!reconciliationLock.runIfFree(stillHoldsTheLock -> check.run())) {
        log.debug(
            "SEB trade listing check skipped, the reconciliation lock is held: trigger={}",
            trigger);
      }
    } catch (RuntimeException e) {
      log.error("SEB trade listing check failed: trigger={}", trigger, e);
    }
  }
}
