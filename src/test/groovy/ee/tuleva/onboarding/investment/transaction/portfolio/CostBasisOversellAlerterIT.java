package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.OVERSELL_ALERTED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

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

  @Autowired private TransactionAuditEventRepository auditEventRepository;

  private final RecordingNotificationService notifications = new RecordingNotificationService();
  private CostBasisOversellAlerter alerter;

  @BeforeEach
  void setUp() {
    alerter = new CostBasisOversellAlerter(auditEventRepository, notifications, CLOCK);
  }

  @Test
  void anOversellIsPostedToTheInvestmentChannelAndRecorded() {
    alerter.alert(TUK75, oversell(TRADE_DATE, "100000.0000", "150000.0000"));

    assertThat(notifications.sent())
        .containsExactly(
            new Sent(
                """
                🔴 TUK75 soetusmaksumus 01.05.2026: IE00BFNM3G45 müük 150000 ühikut, teadaolev positsioon 100000
                Fondi soetusmaksumus ei sisalda seda müüki, kuni see on parandamata
                Leia puuduv ost või vale müügikogus tehinguregistris või algpositsioonis ja paranda see""",
                INVESTMENT));
    assertThat(auditEventRepository.findByEventType(OVERSELL_ALERTED))
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(
            TransactionAuditEvent.builder()
                .eventType(OVERSELL_ALERTED)
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
  }

  @Test
  void theSameOversellOnALaterRunIsNotPostedAgain() {
    alerter.alert(TUK75, oversell(TRADE_DATE, "100000.0000", "150000.0000"));
    alerter.alert(TUK75, oversell(TRADE_DATE, "100000", "150000"));

    assertThat(notifications.sent()).hasSize(1);
    assertThat(auditEventRepository.findByEventType(OVERSELL_ALERTED)).hasSize(1);
  }

  @Test
  void anOversellWhoseQuantitiesChangedIsPostedAgain() {
    alerter.alert(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.alert(TUK75, oversell(TRADE_DATE, "120000", "150000"));

    assertThat(notifications.sent()).hasSize(2);
    assertThat(notifications.sent().getLast().message())
        .startsWith(
            "🔴 TUK75 soetusmaksumus 01.05.2026: IE00BFNM3G45 müük 150000 ühikut, teadaolev"
                + " positsioon 120000\n");
    assertThat(auditEventRepository.findByEventType(OVERSELL_ALERTED)).hasSize(2);
  }

  @Test
  void anOversellReturningToAnEarlierStateIsPostedAgain() {
    alerter.alert(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.alert(TUK75, oversell(TRADE_DATE, "120000", "150000"));
    alerter.alert(TUK75, oversell(TRADE_DATE, "100000", "150000"));

    assertThat(notifications.sent()).hasSize(3);
  }

  @Test
  void anOversellOnAnotherDateIsItsOwnAlert() {
    alerter.alert(TUK75, oversell(TRADE_DATE, "100000", "150000"));
    alerter.alert(TUK75, oversell(TRADE_DATE.plusDays(1), "100000", "150000"));

    assertThat(notifications.sent()).hasSize(2);
  }

  @Test
  void anOversellWhoseSlackPostFailedIsNotRecordedSoTheNextRunPostsIt() {
    notifications.failNext();

    alerter.alert(TUK75, oversell(TRADE_DATE, "100000", "150000"));

    assertThat(notifications.sent()).isEmpty();
    assertThat(auditEventRepository.findByEventType(OVERSELL_ALERTED)).isEmpty();

    alerter.alert(TUK75, oversell(TRADE_DATE, "100000", "150000"));

    assertThat(notifications.sent()).hasSize(1);
    assertThat(auditEventRepository.findByEventType(OVERSELL_ALERTED)).hasSize(1);
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
