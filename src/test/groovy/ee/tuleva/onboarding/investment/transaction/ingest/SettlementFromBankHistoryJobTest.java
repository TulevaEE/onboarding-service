package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.time.LocalDate.EPOCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

import ee.tuleva.onboarding.banking.StoredTradeReferences;
import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementFromBankHistoryJobTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneId.of("Europe/Tallinn"));
  private static final Map<UUID, String> OLDER_REFERENCES =
      Map.of(UUID.fromString("00000000-0000-0000-0000-000000000001"), "DLA0553685");

  @Mock private StoredTradeReferences storedTradeReferences;
  @Mock private SettlementFromBankService settlementFromBankService;
  @Mock private OperationsNotificationService notificationService;

  private SettlementFromBankHistoryJob job() {
    return jobLockedBy(RecordingLockProvider.free());
  }

  private SettlementFromBankHistoryJob jobLockedBy(RecordingLockProvider lockProvider) {
    return new SettlementFromBankHistoryJob(
        storedTradeReferences,
        settlementFromBankService,
        notificationService,
        lockProvider.reconciliationLockAt(CLOCK));
  }

  @Test
  void run_settlesTheHistoryUnderTheReconciliationLock() {
    RecordingLockProvider lockProvider = RecordingLockProvider.free();
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    List<Boolean> lockHeldWhileSettling = new ArrayList<>();
    willAnswer(
            invocation -> {
              lockHeldWhileSettling.add(lockProvider.isHeld());
              return Optional.of(new BankSettlementRun(1, 0, 0, 0, 0));
            })
        .given(settlementFromBankService)
        .settleOrdersBookedSince(eq(EPOCH), eq(OLDER_REFERENCES), any());

    jobLockedBy(lockProvider).run();

    assertThat(lockHeldWhileSettling).containsExactly(true);
    assertThat(lockProvider.lockNames()).containsExactly(SebReconciliationLock.NAME);
  }

  @Test
  void run_whileTheReconciliationLockIsHeld_settlesNothingWarnsAndFailsItsTrigger() {
    SettlementFromBankHistoryJob lockedOut = jobLockedBy(RecordingLockProvider.heldElsewhere());

    assertThatThrownBy(lockedOut::run)
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(JobBusyException.class);

    then(settlementFromBankService).shouldHaveNoInteractions();
    then(notificationService)
        .should()
        .sendMessage(
            "Registri tellimuste arveldamine panga kuupäevadega ei alanud, sest SEB ootel-tehingute"
                + " sobitamine käib.\n"
                + "Midagi ei muudetud.\n"
                + "Käivita uuesti, kui see on lõppenud: INSERT INTO public.investment_job_trigger"
                + " (job_name) VALUES ('SettlementFromBankHistoryJob')",
            INVESTMENT,
            WARNING);
  }

  @Test
  void run_thatLosesTheLock_waitsAsBusyForTheNextPollWithoutPostingAFailure() {
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    given(settlementFromBankService.settleOrdersBookedSince(any(), any(), any()))
        .willThrow(new SettlementFromBankLockLostException(42L));

    assertThatThrownBy(() -> job().run()).isInstanceOf(JobBusyException.class);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void run_settlesTheWholeHistoryWithTheReferencesOfOlderBookingsAndPostsTheCounts() {
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    given(settlementFromBankService.settleOrdersBookedSince(eq(EPOCH), eq(OLDER_REFERENCES), any()))
        .willReturn(Optional.of(new BankSettlementRun(12, 192, 6, 4, 0)));

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
    given(settlementFromBankService.settleOrdersBookedSince(eq(EPOCH), eq(OLDER_REFERENCES), any()))
        .willReturn(Optional.of(new BankSettlementRun(1, 0, 0, 1, 1)));

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
  void run_withoutAUsableSebPendingReport_saysSoAndChangesNothing() {
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    given(settlementFromBankService.settleOrdersBookedSince(eq(EPOCH), eq(OLDER_REFERENCES), any()))
        .willReturn(Optional.empty());

    job().run();

    then(notificationService)
        .should()
        .sendMessage(
            "Registri tellimused ei saanud panga kuupäeva: viimane SEB ootel-tehingute raport on"
                + " tühi, vigaste ridadega või võib olla kärbitud (vt logi).\n"
                + "Midagi ei muudetud.\n"
                + "Käivita uuesti pärast järgmist raportit: INSERT INTO"
                + " public.investment_job_trigger (job_name) VALUES"
                + " ('SettlementFromBankHistoryJob')",
            INVESTMENT,
            WARNING);
  }

  @Test
  void run_thatFails_saysSoAndFailsItsTrigger() {
    given(storedTradeReferences.byLedgerExternalReference()).willReturn(OLDER_REFERENCES);
    given(settlementFromBankService.settleOrdersBookedSince(any(), any(), any()))
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
