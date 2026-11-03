package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.time.LocalDate.EPOCH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.banking.StoredTradeReferences;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementFromBankHistoryJobTest {

  private static final Map<UUID, String> OLDER_REFERENCES =
      Map.of(UUID.fromString("00000000-0000-0000-0000-000000000001"), "DLA0553685");

  @Mock private StoredTradeReferences storedTradeReferences;
  @Mock private SettlementFromBankService settlementFromBankService;
  @Mock private OperationsNotificationService notificationService;

  private SettlementFromBankHistoryJob job() {
    return new SettlementFromBankHistoryJob(
        storedTradeReferences, settlementFromBankService, notificationService);
  }

  @Test
  void run_settlesTheWholeHistoryWithTheReferencesOfOlderBookingsAndPostsTheCounts() {
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    given(settlementFromBankService.settleOrdersBookedSince(EPOCH, OLDER_REFERENCES))
        .willReturn(new BankSettlementRun(12, 192, 6, 4));

    job().run();

    then(notificationService)
        .should()
        .sendMessage(
            "Registri tellimuste arveldused said kogu ajaloo ulatuses panga kirjenduse kuupäeva.\n"
                + "Panga järgi arveldati 12 tellimust; varem arveldatutest jäi 192 kuupäev samaks"
                + " ja 6 oma muutus. Pangakuupäevata jäi 4 tellimust.\n"
                + "Need, mille tükki pank pole kirjendanud, mis on täitmata või mille SEB viide"
                + " ei klapi: SELECT o.id, o.order_status, s.source FROM"
                + " public.investment_transaction_order o LEFT JOIN public.transaction_settlement s"
                + " ON s.order_id = o.id WHERE o.order_status IN ('EXECUTED', 'SETTLED')"
                + " AND s.source IS DISTINCT FROM 'BANK_STATEMENT'",
            INVESTMENT);
  }

  @Test
  void run_thatFails_saysSoAndHowToRunItAgain() {
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    given(settlementFromBankService.settleOrdersBookedSince(any(), any()))
        .willThrow(new IllegalStateException("boom"));

    job().run();

    then(notificationService)
        .should()
        .sendMessage(
            "Registri tellimuste arvelduste täitmine panga kuupäevadega katkes (põhjus on"
                + " logis).\nMidagi ei salvestatud.\nParanda põhjus ja käivita uuesti: INSERT INTO"
                + " public.investment_job_trigger (job_name) VALUES ('SettlementFromBankHistoryJob')",
            INVESTMENT,
            ERROR);
  }
}
