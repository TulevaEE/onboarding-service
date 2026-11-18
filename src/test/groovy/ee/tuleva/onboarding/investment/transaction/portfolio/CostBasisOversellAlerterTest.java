package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.OVERSELL_ALERTED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CostBasisOversellAlerterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-02T00:30:00Z"), ZoneId.of("Europe/Tallinn"));
  private static final CostBasisOversellException OVERSELL =
      new CostBasisOversellException(
          TUK75.getIsin(),
          "IE00BFNM3G45",
          LocalDate.of(2026, 5, 1),
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
  void anUnreadableAlertHistoryStillPostsTheOversell() {
    given(
            auditEventRepository.findByEventTypeAndDedupKey(
                OVERSELL_ALERTED, "TUK75 IE00BFNM3G45 2026-05-01"))
        .willThrow(new IllegalStateException("database unavailable"));

    alerter.alert(TUK75, OVERSELL);

    verify(notificationService)
        .sendMessage(
            """
            🔴 TUK75 soetusmaksumus 01.05.2026: IE00BFNM3G45 müük 150000 ühikut, teadaolev positsioon 100000
            Fondi soetusmaksumus ei sisalda seda müüki, kuni see on parandamata
            Leia puuduv ost või vale müügikogus tehinguregistris või algpositsioonis ja paranda see""",
            INVESTMENT);
  }

  @Test
  void aPostedOversellThatCannotBeRecordedDoesNotStopTheRun() {
    given(auditEventRepository.save(any()))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThatCode(() -> alerter.alert(TUK75, OVERSELL)).doesNotThrowAnyException();

    verify(notificationService).sendMessage(anyString(), any());
  }
}
