package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.time.LocalDate.EPOCH;

import ee.tuleva.onboarding.banking.StoredTradeReferences;
import ee.tuleva.onboarding.investment.event.RunSettlementFromBankHistoryRequested;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
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

  private final StoredTradeReferences storedTradeReferences;
  private final SettlementFromBankService settlementFromBankService;
  private final OperationsNotificationService notificationService;

  @EventListener(RunSettlementFromBankHistoryRequested.class)
  void run() {
    BankSettlementRun run;
    try {
      run =
          settlementFromBankService.settleOrdersBookedSince(
              EPOCH, storedTradeReferences.byLedgerExternalReference());
    } catch (RuntimeException e) {
      log.error("Settling the registry's history from the bank failed", e);
      notificationService.sendMessage(failedMessage(), INVESTMENT, ERROR);
      return;
    }
    log.info("Settled the registry's history from the bank: run={}", run);
    notificationService.sendMessage(completedMessage(run), INVESTMENT);
  }

  private static String completedMessage(BankSettlementRun run) {
    return "Registri tellimuste arveldused said kogu ajaloo ulatuses panga kirjenduse kuupäeva.\n"
        + "Panga järgi arveldati "
        + run.settled()
        + " tellimust; varem arveldatutest jäi "
        + run.sameDate()
        + " kuupäev samaks ja "
        + run.newDate()
        + " oma muutus. Pangakuupäevata jäi "
        + run.withoutBankDate()
        + " tellimust.\n"
        + "Need, mille tükki pank pole kirjendanud, mis on täitmata või mille SEB viide ei klapi:"
        + " SELECT o.id, o.order_status, s.source FROM public.investment_transaction_order o"
        + " LEFT JOIN public.transaction_settlement s ON s.order_id = o.id"
        + " WHERE o.order_status IN ('EXECUTED', 'SETTLED')"
        + " AND s.source IS DISTINCT FROM 'BANK_STATEMENT'";
  }

  private static String failedMessage() {
    return "Registri tellimuste arvelduste täitmine panga kuupäevadega katkes (põhjus on logis).\n"
        + "Midagi ei salvestatud.\n"
        + "Paranda põhjus ja käivita uuesti: INSERT INTO public.investment_job_trigger (job_name)"
        + " VALUES ('SettlementFromBankHistoryJob')";
  }
}
