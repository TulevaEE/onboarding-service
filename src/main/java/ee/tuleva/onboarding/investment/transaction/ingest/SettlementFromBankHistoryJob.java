package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.time.LocalDate.EPOCH;

import ee.tuleva.onboarding.banking.StoredTradeReferences;
import ee.tuleva.onboarding.investment.event.RunSettlementFromBankHistoryRequested;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class SettlementFromBankHistoryJob {

  private static final String FAILED_MESSAGE =
      "Registri tellimuste arveldamine panga kuupäevadega katkes (põhjus on logis).\n"
          + "Ükski tellimus ei saanud selle käivitusega panga kuupäeva.\n"
          + "Paranda põhjus ja käivita uuesti: INSERT INTO public.investment_job_trigger"
          + " (job_name) VALUES ('SettlementFromBankHistoryJob')";

  private static final String NO_USABLE_REPORT_MESSAGE =
      "Registri tellimused ei saanud panga kuupäeva: viimane SEB ootel-tehingute raport on tühi,"
          + " vigaste ridadega või võib olla kärbitud (vt logi).\n"
          + "Midagi ei muudetud.\n"
          + "Käivita uuesti pärast järgmist raportit: INSERT INTO public.investment_job_trigger"
          + " (job_name) VALUES ('SettlementFromBankHistoryJob')";

  private final StoredTradeReferences storedTradeReferences;
  private final SettlementFromBankService settlementFromBankService;
  private final OperationsNotificationService notificationService;

  @EventListener(RunSettlementFromBankHistoryRequested.class)
  void run() {
    Optional<BankSettlementRun> settled;
    try {
      settled =
          settlementFromBankService.settleOrdersBookedSince(
              EPOCH, storedTradeReferences.byLedgerExternalReference());
    } catch (RuntimeException e) {
      notificationService.sendMessage(FAILED_MESSAGE, INVESTMENT, ERROR);
      throw e;
    }
    if (settled.isEmpty()) {
      notificationService.sendMessage(NO_USABLE_REPORT_MESSAGE, INVESTMENT, WARNING);
      return;
    }
    BankSettlementRun run = settled.get();
    log.info("Settled the registry's history from the bank: run={}", run);
    if (run.failed() == 0) {
      notificationService.sendMessage(completedMessage(run), INVESTMENT);
    } else {
      notificationService.sendMessage(completedMessage(run), INVESTMENT, WARNING);
    }
  }

  private static String completedMessage(BankSettlementRun run) {
    return "Registri tellimuste arveldusi võrreldi panga kirjendustega kogu ajaloo ulatuses.\n"
        + "Panga kuupäeva said "
        + orders(run.settled() + run.sameDate() + run.newDate())
        + ": "
        + run.settled()
        + " arveldati alles nüüd, "
        + run.sameDate()
        + " varasem kuupäev oli sama, "
        + run.newDate()
        + "-l see muutus. Pangakuupäevata jäi "
        + orders(run.withoutBankDate())
        + (run.failed() == 0 ? "" : ", ebaõnnestus " + orders(run.failed()) + " (põhjus on logis)")
        + ".\n"
        + "Pangakuupäevata tellimused: SELECT o.id, o.order_status, s.source FROM"
        + " public.investment_transaction_order o LEFT JOIN public.transaction_settlement s"
        + " ON s.order_id = o.id WHERE o.order_status IN ('EXECUTED', 'SETTLED')"
        + " AND s.source IS DISTINCT FROM 'BANK_STATEMENT'";
  }

  private static String orders(int count) {
    return count == 1 ? "1 tellimus" : count + " tellimust";
  }
}
