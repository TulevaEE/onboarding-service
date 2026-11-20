package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SebTradeListingAlertsTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-02-16T08:15:00Z"), ZoneId.of("Europe/Tallinn"));

  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final TransactionAuditEventRepository auditEventRepository =
      mock(TransactionAuditEventRepository.class);
  private final SebTradeListingAlerts alerts =
      new SebTradeListingAlerts(notificationService, auditEventRepository, CLOCK);

  @Test
  void aSaleAnOrderWithoutASendTimeAndASkippedCheck_goOutInOneMessageAndAreEachRecorded() {
    TransactionOrder sale = sale();

    alerts.publish(
        List.of(new DroppedTrade(sale, "DLA0000001", LocalDate.of(2026, 2, 12))),
        List.of(sale),
        List.of(SkippedListingCheck.NEVER_LISTED_ORDERS));

    then(notificationService)
        .should()
        .sendMessage(
            "⚠️ SEB tehing kadus ootel-tehingutest, pangas makset pole: 1\n"
                + "TUK00 · DLA0000001 · müük IE0000000001 · SEB arvelduspäev 12.02.2026\n"
                + "Küsi SEB-lt, miks tehing nimekirjast kadus ja kas see arveldub.\n\n"
                + "⚠️ SEB pole tehingut ootel-tehingutesse pannud: 1\n"
                + "TUK00 · tehingukorraldus 7 · müük IE0000000001 · saadetud ?\n"
                + "Kontrolli SEB-lt, kas tehingukorraldus jõudis kohale.\n\n"
                + "⚠️ SEB ootel-tehingute kontroll jäi tegemata: SEB-sse jõudmata"
                + " tehingukorraldusi ei saanud kontrollida.\n"
                + "Teata IT-le, viga on logis. Kontroll kordub järgmisel tööpäeval.",
            INVESTMENT,
            WARNING);
    then(auditEventRepository).should(times(3)).save(any(TransactionAuditEvent.class));
  }

  @Test
  void aMessageSlackRefuses_isNotRecorded_soTheNextRunSendsItAgain() {
    willThrow(new IllegalStateException("slack down"))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT), eq(WARNING));

    alerts.publish(List.of(), List.of(sale()), List.of());

    then(auditEventRepository).should(never()).save(any(TransactionAuditEvent.class));
  }

  @Test
  void anUnreadableAlertHistory_alertsAgainRatherThanStayingQuiet() {
    given(auditEventRepository.findByEventTypeAndDedupKey(anyString(), anyString()))
        .willThrow(new IllegalStateException("database down"));

    alerts.publish(List.of(), List.of(sale()), List.of());

    then(notificationService).should().sendMessage(anyString(), eq(INVESTMENT), eq(WARNING));
  }

  @Test
  void aRecordThatFailsAfterTheMessageWentOut_doesNotFailTheRun() {
    given(auditEventRepository.save(any(TransactionAuditEvent.class)))
        .willThrow(new IllegalStateException("database down"));

    alerts.publish(List.of(), List.of(sale()), List.of());

    then(notificationService).should().sendMessage(anyString(), eq(INVESTMENT), eq(WARNING));
  }

  @Test
  void nothingNew_sendsNothing() {
    alerts.publish(List.of(), List.of(), List.of());

    then(notificationService).shouldHaveNoInteractions();
  }

  private static TransactionOrder sale() {
    return TransactionOrder.builder()
        .id(7L)
        .fund(TUK00)
        .instrumentIsin("IE0000000001")
        .transactionType(SELL)
        .orderUuid(UUID.fromString("00000000-0000-0000-0000-000000000007"))
        .build();
  }
}
