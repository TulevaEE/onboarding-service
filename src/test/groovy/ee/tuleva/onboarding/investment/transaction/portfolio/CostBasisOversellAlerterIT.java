package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.OVERSELL_CLEARED;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.OVERSELL_FOUND;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.OVERSELL_POSTED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.portfolio.RecordingNotificationService.Sent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class CostBasisOversellAlerterIT {

  private static final Instant NOW = Instant.parse("2026-05-02T00:30:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Europe/Tallinn"));
  private static final String INSTRUMENT_ISIN = "IE00BFNM3G45";
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 5, 1);
  private static final String FOUND_MESSAGE =
      """
      🔴 TUK75 soetusmaksumus 01.05.2026: IE00BFNM3G45 müük 150000 ühikut, teadaolev positsioon 100000
      Fondi soetusmaksumus on sellest päevast alates vale, kuni see on parandamata
      Leia puuduv ost või vale müügikogus tehinguregistris ja paranda see hiljemalt 14.05.2026""";
  private static final String CLEARED_MESSAGE =
      "✅ TUK75 soetusmaksumus 01.05.2026: IE00BFNM3G45 müük ei ületa enam positsiooni,"
          + " soetusmaksumus on ümber arvutatud";

  @Autowired private TransactionAuditEventRepository auditEventRepository;

  private final RecordingNotificationService notifications = new RecordingNotificationService();
  private CostBasisOversellAlerter alerter;

  @BeforeEach
  void setUp() {
    alerter = new CostBasisOversellAlerter(auditEventRepository, notifications, CLOCK);
  }

  @Test
  void aNewOversellIsRecordedAndPostedToTheInvestmentChannel() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000.0000", "150000.0000"));
    alerter.postPending();

    assertThat(notifications.sent()).containsExactly(new Sent(FOUND_MESSAGE, INVESTMENT));
    TransactionAuditEvent found = auditEventRepository.findByEventType(OVERSELL_FOUND).getFirst();
    assertThat(found)
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(
            TransactionAuditEvent.builder()
                .eventType(OVERSELL_FOUND)
                .dedupKey("TUK75 IE00BFNM3G45 2026-05-01")
                .actor("system")
                .payload(
                    Map.of(
                        "fund", "TUK75",
                        "instrumentIsin", INSTRUMENT_ISIN,
                        "asOfDate", "2026-05-01",
                        "heldQuantity", "100000",
                        "sellQuantity", "150000"))
                .createdAt(NOW)
                .build());
    assertThat(auditEventRepository.findByEventType(OVERSELL_POSTED))
        .extracting(TransactionAuditEvent::getDedupKey, TransactionAuditEvent::getPayload)
        .containsExactly(
            tuple("TUK75 IE00BFNM3G45 2026-05-01", Map.of("eventId", found.getId().toString())));
  }

  @Test
  void theSameOversellOnALaterRunIsNeitherRecordedNorPostedAgain() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000.0000", "150000.0000"));
    alerter.postPending();
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.postPending();

    assertThat(notifications.sent()).hasSize(1);
    assertThat(auditEventRepository.findByEventType(OVERSELL_FOUND)).hasSize(1);
  }

  @Test
  void anOversellWhoseQuantitiesChangedIsPostedAgain() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.postPending();
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "120000", "150000"));
    alerter.postPending();

    assertThat(notifications.sent()).hasSize(2);
    assertThat(notifications.sent().getLast().message())
        .startsWith(
            "🔴 TUK75 soetusmaksumus 01.05.2026: IE00BFNM3G45 müük 150000 ühikut, teadaolev"
                + " positsioon 120000\n");
  }

  @Test
  void anOversellOnAnotherDateIsItsOwnAlert() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.recordFound(TUK75, oversell(TRADE_DATE.plusDays(1), "100000", "150000"));
    alerter.postPending();

    assertThat(notifications.sent()).hasSize(2);
  }

  @Test
  void aFailedPostIsRetriedOnTheNextRunEvenWhenTheOversellIsNotFoundAgain() {
    notifications.failNext();
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.postPending();

    assertThat(notifications.sent()).isEmpty();
    assertThat(auditEventRepository.findByEventType(OVERSELL_FOUND)).hasSize(1);
    assertThat(auditEventRepository.findByEventType(OVERSELL_POSTED)).isEmpty();

    alerter.postPending();

    assertThat(notifications.sent()).containsExactly(new Sent(FOUND_MESSAGE, INVESTMENT));
  }

  @Test
  void aFailedPostHoldsBackTheLaterAlertsSoTheyArePostedInOrder() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.postPending();
    alerter.clearResolved(TUK75, TRADE_DATE, TRADE_DATE);
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "90000", "150000"));
    notifications.failNext();
    alerter.postPending();

    assertThat(notifications.sent()).hasSize(1);

    alerter.postPending();

    assertThat(notifications.sent())
        .extracting(Sent::message)
        .satisfiesExactly(
            first -> assertThat(first).isEqualTo(FOUND_MESSAGE),
            second -> assertThat(second).isEqualTo(CLEARED_MESSAGE),
            third -> assertThat(third).contains("teadaolev positsioon 90000\n"));
  }

  @Test
  void anOversellInsideTheRebuiltRangeIsClearedOnceTheRebuildSucceeds() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.postPending();
    alerter.clearResolved(TUK75, TRADE_DATE.minusDays(14), TRADE_DATE);
    alerter.postPending();
    alerter.clearResolved(TUK75, TRADE_DATE.minusDays(14), TRADE_DATE);
    alerter.postPending();

    assertThat(notifications.sent())
        .containsExactly(
            new Sent(FOUND_MESSAGE, INVESTMENT), new Sent(CLEARED_MESSAGE, INVESTMENT));
    assertThat(auditEventRepository.findByEventType(OVERSELL_CLEARED))
        .extracting(TransactionAuditEvent::getDedupKey, TransactionAuditEvent::getPayload)
        .containsExactly(
            tuple(
                "TUK75 IE00BFNM3G45 2026-05-01",
                Map.of(
                    "fund", "TUK75", "instrumentIsin", INSTRUMENT_ISIN, "asOfDate", "2026-05-01")));
  }

  @Test
  void anOversellOutsideTheRebuiltRangeStaysOpen() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.clearResolved(TUK75, TRADE_DATE.plusDays(1), TRADE_DATE.plusDays(15));

    assertThat(auditEventRepository.findByEventType(OVERSELL_CLEARED)).isEmpty();
  }

  @Test
  void anotherFundsRebuildDoesNotClearIt() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.clearResolved(TUK00, TRADE_DATE, TRADE_DATE);

    assertThat(auditEventRepository.findByEventType(OVERSELL_CLEARED)).isEmpty();
  }

  @Test
  void anOversellFoundAgainAfterItWasClearedIsPostedAgain() {
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.postPending();
    alerter.clearResolved(TUK75, TRADE_DATE, TRADE_DATE);
    alerter.postPending();
    alerter.recordFound(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.postPending();

    assertThat(notifications.sent())
        .extracting(Sent::message)
        .containsExactly(FOUND_MESSAGE, CLEARED_MESSAGE, FOUND_MESSAGE);
  }

  private static CostBasisOversellException oversell(
      LocalDate asOfDate, String heldQuantity, String sellQuantity) {
    return new CostBasisOversellException(
        TUK75.getIsin(),
        INSTRUMENT_ISIN,
        asOfDate,
        new BigDecimal(heldQuantity),
        new BigDecimal(sellQuantity));
  }
}
