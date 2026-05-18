package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverdueSettlementJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18); // Monday

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private EmailService emailService;

  private final Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  private final AlertProperties alertProperties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));

  private OverdueSettlementJob job() {
    return new OverdueSettlementJob(
        clock, orderRepository, new AlertMandrillMessageFactory(alertProperties), emailService);
  }

  @Test
  void run_etfOverdueBeyondThreeBusinessDays_sendsAlert() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    // ETF threshold: today minus 3 business days. From 2026-05-18 Mon, 3 bdays back = 2026-05-13
    // Wed.
    // expectedSettlementDate = 2026-05-12 (Tue) is older than threshold => overdue.
    TransactionOrder overdueEtf =
        order(1L, ETF, SENT, LocalDate.of(2026, 5, 12), TKF100, "IE000F60HVH9");
    TransactionOrder boundaryEtf =
        order(2L, ETF, SENT, LocalDate.of(2026, 5, 13), TKF100, "IE000F60HVH9");
    given(orderRepository.findOverdueOrders(SENT, EXECUTED))
        .willReturn(List.of(overdueEtf, boundaryEtf));

    job().run();

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());

    MandrillMessage message = captor.getValue();
    assertThat(message.getSubject()).isEqualTo("HOIATUS: 1 hilinenud tehing(ut) ootel");
    assertThat(message.getText())
        .contains("Order id: 1")
        .contains("ISIN: IE000F60HVH9")
        .contains("Expected settlement: 2026-05-12")
        .doesNotContain("Order id: 2");
  }

  @Test
  void run_fundOverdueBeyondFiveBusinessDays_sendsAlert() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    // FUND threshold: today minus 5 business days. From 2026-05-18 Mon, 5 bdays back = 2026-05-11
    // Mon.
    // expectedSettlementDate = 2026-05-08 (Fri) is older than threshold => overdue.
    TransactionOrder overdueFund =
        order(3L, FUND, EXECUTED, LocalDate.of(2026, 5, 8), TUK00, "EE3600109443");
    TransactionOrder boundaryFund =
        order(4L, FUND, SENT, LocalDate.of(2026, 5, 11), TUK00, "EE3600109443");
    given(orderRepository.findOverdueOrders(SENT, EXECUTED))
        .willReturn(List.of(overdueFund, boundaryFund));

    job().run();

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());

    MandrillMessage message = captor.getValue();
    assertThat(message.getSubject()).isEqualTo("HOIATUS: 1 hilinenud tehing(ut) ootel");
    assertThat(message.getText()).contains("Order id: 3").doesNotContain("Order id: 4");
  }

  @Test
  void run_noOverdueOrders_doesNotSendAlert() {
    given(orderRepository.findOverdueOrders(SENT, EXECUTED)).willReturn(List.of());

    job().run();

    verify(emailService, never()).sendSystemEmail(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void run_multipleOverdueOrders_listsAllInBody() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    TransactionOrder etf = order(10L, ETF, SENT, LocalDate.of(2026, 5, 12), TKF100, "IE000F60HVH9");
    TransactionOrder fund =
        order(11L, FUND, EXECUTED, LocalDate.of(2026, 5, 8), TUK00, "EE3600109443");
    given(orderRepository.findOverdueOrders(SENT, EXECUTED)).willReturn(List.of(etf, fund));

    job().run();

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    assertThat(captor.getValue().getSubject()).isEqualTo("HOIATUS: 2 hilinenud tehing(ut) ootel");
    assertThat(captor.getValue().getText()).contains("Order id: 10").contains("Order id: 11");
  }

  private static TransactionOrder order(
      long id,
      ee.tuleva.onboarding.investment.transaction.InstrumentType instrumentType,
      OrderStatus orderStatus,
      LocalDate expectedSettlementDate,
      ee.tuleva.onboarding.fund.TulevaFund fund,
      String isin) {
    return TransactionOrder.builder()
        .id(id)
        .fund(fund)
        .instrumentIsin(isin)
        .instrumentType(instrumentType)
        .transactionType(BUY)
        .orderStatus(orderStatus)
        .orderVenue(OrderVenue.SEB)
        .expectedSettlementDate(expectedSettlementDate)
        .orderUuid(UUID.randomUUID())
        .build();
  }
}
