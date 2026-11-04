package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.time.LocalDate.EPOCH;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        .willReturn(new BankSettlementRun(12, 192, 6, 4, 0));

    job().run();

    then(notificationService)
        .should()
        .sendMessage(
            "Registri tellimuste arveldusi võrreldi panga kirjendustega kogu ajaloo ulatuses.\n"
                + "Panga kuupäeva said 210 tellimust: 12 arveldati alles nüüd, 192 varasem"
                + " kuupäev oli sama, 6-l see muutus. Pangakuupäevata jäi 4 tellimust.\n"
                + "Pangakuupäevata tellimused: SELECT o.id, o.order_status, s.source FROM"
                + " public.investment_transaction_order o LEFT JOIN public.transaction_settlement"
                + " s ON s.order_id = o.id WHERE o.order_status IN ('EXECUTED', 'SETTLED') AND"
                + " s.source IS DISTINCT FROM 'BANK_STATEMENT'",
            INVESTMENT);
  }

  @Test
  void run_withOrdersThatFailed_warnsAndCountsThem() {
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    given(settlementFromBankService.settleOrdersBookedSince(EPOCH, OLDER_REFERENCES))
        .willReturn(new BankSettlementRun(1, 0, 0, 1, 1));

    job().run();

    then(notificationService)
        .should()
        .sendMessage(
            "Registri tellimuste arveldusi võrreldi panga kirjendustega kogu ajaloo ulatuses.\n"
                + "Panga kuupäeva said 1 tellimus: 1 arveldati alles nüüd, 0 varasem kuupäev oli"
                + " sama, 0-l see muutus. Pangakuupäevata jäi 1 tellimus, ebaõnnestus 1 tellimus"
                + " (põhjus on logis).\n"
                + "Pangakuupäevata tellimused: SELECT o.id, o.order_status, s.source FROM"
                + " public.investment_transaction_order o LEFT JOIN public.transaction_settlement"
                + " s ON s.order_id = o.id WHERE o.order_status IN ('EXECUTED', 'SETTLED') AND"
                + " s.source IS DISTINCT FROM 'BANK_STATEMENT'",
            INVESTMENT,
            WARNING);
  }

  @Test
  void run_thatFails_saysSoAndFailsItsTrigger() {
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    given(settlementFromBankService.settleOrdersBookedSince(any(), any()))
        .willThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> job().run()).isInstanceOf(IllegalStateException.class);

    then(notificationService)
        .should()
        .sendMessage(
            "Registri tellimuste arveldamine panga kuupäevadega katkes (põhjus on logis).\n"
                + "Ükski tellimus ei saanud selle käivitusega panga kuupäeva.\n"
                + "Paranda põhjus ja käivita uuesti: INSERT INTO public.investment_job_trigger"
                + " (job_name) VALUES ('SettlementFromBankHistoryJob')",
            INVESTMENT,
            ERROR);
  }
}
