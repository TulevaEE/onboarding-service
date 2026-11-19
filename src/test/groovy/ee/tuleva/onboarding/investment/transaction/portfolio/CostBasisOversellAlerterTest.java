package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.OVERSELL_CLEARED;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.OVERSELL_FOUND;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.OVERSELL_POSTED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CostBasisOversellAlerterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-02T00:30:00Z"), ZoneId.of("Europe/Tallinn"));
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 5, 1);
  private static final CostBasisOversellException OVERSELL =
      new CostBasisOversellException(
          TUK75.getIsin(),
          "IE00BFNM3G45",
          TRADE_DATE,
          new BigDecimal("100000"),
          new BigDecimal("150000"));

  @Mock private TransactionAuditEventRepository auditEventRepository;
  @Mock private OperationsNotificationService notificationService;

  private CostBasisOversellAlerter alerter;

  @BeforeEach
  void setUp() {
    alerter = new CostBasisOversellAlerter(auditEventRepository, notificationService, CLOCK);
  }

  @Test
  void anOversellThatCannotBeRecordedIsPostedDirectlyAndAsksForAManualRecompute() {
    given(auditEventRepository.findByEventTypeAndDedupKey(anyString(), anyString()))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThatCode(() -> alerter.recordFound(TUK75, OVERSELL)).doesNotThrowAnyException();

    verify(notificationService)
        .sendMessage(
            """
            🔴 TUK75 soetusmaksumus 01.05.2026: IE00BFNM3G45 müük 150000 ühikut, teadaolev positsioon 100000
            Fondi soetusmaksumus on sellest päevast alates vale, kuni see on parandamata
            Leia puuduv ost või vale müügikogus tehinguregistris ja paranda see; hoiatust ei õnnestunud salvestada, seega öine ümberarvutus seda päeva ise ei korda ja pärast parandust vajab soetusmaksumus käsitsi ümberarvutust""",
            INVESTMENT);
  }

  @Test
  void anUnreadableAlertHistoryWhileClearingDoesNotStopTheRun() {
    given(auditEventRepository.findByEventType(OVERSELL_FOUND))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThatCode(() -> alerter.clearOpen(TUK75, TRADE_DATE)).doesNotThrowAnyException();
  }

  @Test
  void anUnreadableAlertHistoryWhilePostingDoesNotStopTheRun() {
    given(auditEventRepository.findByEventType(OVERSELL_POSTED))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThatCode(() -> alerter.postPending()).doesNotThrowAnyException();

    verifyNoInteractions(notificationService);
  }

  @Test
  void aPostedAlertThatCannotBeRecordedHoldsBackTheRestSoTheOrderIsKept() {
    given(auditEventRepository.findByEventType(OVERSELL_POSTED)).willReturn(List.of());
    given(auditEventRepository.findByEventType(OVERSELL_FOUND))
        .willReturn(List.of(found(1L, "2026-05-01"), found(2L, "2026-05-04")));
    given(auditEventRepository.findByEventType(OVERSELL_CLEARED)).willReturn(List.of());
    given(auditEventRepository.save(any()))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThatCode(() -> alerter.postPending()).doesNotThrowAnyException();

    verify(notificationService, times(1)).sendMessage(anyString(), any());
  }

  @Test
  void anEventWithAnUnreadableDateIsSkippedAndTheOtherAlertsArePosted() {
    given(auditEventRepository.findByEventType(OVERSELL_POSTED)).willReturn(List.of());
    given(auditEventRepository.findByEventType(OVERSELL_FOUND))
        .willReturn(List.of(found(1L, "not a date"), found(2L, "2026-05-04")));
    given(auditEventRepository.findByEventType(OVERSELL_CLEARED)).willReturn(List.of());

    alerter.postPending();

    verify(notificationService, times(1)).sendMessage(anyString(), any());
    verify(notificationService)
        .sendMessage(
            org.mockito.ArgumentMatchers.startsWith("🔴 TUK75 soetusmaksumus 04.05.2026:"), any());
  }

  @Test
  void anUnreadableAlertHistoryIsNotReadAsNoOpenOversell() {
    given(auditEventRepository.findByEventType(OVERSELL_FOUND))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThatThrownBy(() -> alerter.hasOpenOversell(TUK75))
        .isInstanceOf(IllegalStateException.class);
  }

  private static TransactionAuditEvent found(long id, String asOfDate) {
    return TransactionAuditEvent.builder()
        .id(id)
        .eventType(OVERSELL_FOUND)
        .dedupKey("TUK75 IE00BFNM3G45 " + asOfDate)
        .payload(
            Map.of(
                "fund", "TUK75",
                "instrumentIsin", "IE00BFNM3G45",
                "asOfDate", asOfDate,
                "heldQuantity", "100000",
                "sellQuantity", "150000"))
        .build();
  }
}
