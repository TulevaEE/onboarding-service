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
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
  void run_etfOverdueBeyondThreeBusinessDaysAfterOrderTimestamp_sendsAlert() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    // ETF threshold: orderTimestamp + 3 business days.
    // Mon 2026-05-11 + 3 bdays = Thu 2026-05-14. Today is Mon 2026-05-18 > Thu => overdue.
    TransactionOrder overdueEtf =
        order(1L, ETF, SENT, dateOnly(2026, 5, 11), TKF100, "IE000F60HVH9");
    // Wed 2026-05-13 + 3 bdays = Mon 2026-05-18. Today is Mon 2026-05-18, not strictly greater =>
    // boundary, not overdue yet.
    TransactionOrder boundaryEtf =
        order(2L, ETF, SENT, dateOnly(2026, 5, 13), TKF100, "IE000F60HVH9");
    given(orderRepository.findOverdueOrders(SENT, EXECUTED))
        .willReturn(List.of(overdueEtf, boundaryEtf));

    job().run();

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());

    MandrillMessage message = captor.getValue();
    assertThat(message.getSubject()).isEqualTo("HOIATUS: 1 hilinenud tehing(ut) ootel");
    assertThat(message.getText()).contains("Order id: 1").doesNotContain("Order id: 2");
  }

  @Test
  void run_etfFirstAlertOnFourthBusinessDayAfterOrderTimestamp_sendsAlert() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    // Boundary: ETF orderTimestamp = Mon 2026-05-11.
    // +3 business days = Thu 2026-05-14 (deadline).
    // Today = Fri 2026-05-15 (Mon + 4 bdays, one day past deadline) — first alert fires.
    Clock fridayClock =
        Clock.fixed(LocalDate.of(2026, 5, 15).atStartOfDay(TALLINN).toInstant(), TALLINN);
    OverdueSettlementJob friJob =
        new OverdueSettlementJob(
            fridayClock,
            orderRepository,
            new AlertMandrillMessageFactory(alertProperties),
            emailService);

    TransactionOrder etf = order(1L, ETF, SENT, dateOnly(2026, 5, 11), TKF100, "IE000F60HVH9");
    given(orderRepository.findOverdueOrders(SENT, EXECUTED)).willReturn(List.of(etf));

    friJob.run();

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    assertThat(captor.getValue().getSubject()).isEqualTo("HOIATUS: 1 hilinenud tehing(ut) ootel");
  }

  @Test
  void run_fundOverdueBeyondFiveBusinessDaysAfterOrderTimestamp_sendsAlert() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    // FUND threshold: orderTimestamp + 5 business days.
    // Mon 2026-05-04 + 5 bdays = Mon 2026-05-11. Today Mon 2026-05-18 > Mon 2026-05-11 => overdue.
    TransactionOrder overdueFund =
        order(3L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUK00, "EE3600109443");
    // Mon 2026-05-11 + 5 bdays = Mon 2026-05-18. Today is Mon 2026-05-18 => boundary, not overdue.
    TransactionOrder boundaryFund =
        order(4L, FUND, SENT, dateOnly(2026, 5, 11), TUK00, "EE3600109443");
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

    TransactionOrder etf = order(10L, ETF, SENT, dateOnly(2026, 5, 11), TKF100, "IE000F60HVH9");
    TransactionOrder fund = order(11L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUK00, "EE3600109443");
    given(orderRepository.findOverdueOrders(SENT, EXECUTED)).willReturn(List.of(etf, fund));

    job().run();

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    assertThat(captor.getValue().getSubject()).isEqualTo("HOIATUS: 2 hilinenud tehing(ut) ootel");
    assertThat(captor.getValue().getText()).contains("Order id: 10").contains("Order id: 11");
  }

  @Test
  void run_orderTimestampMissing_doesNotConsiderOverdue() {
    given(orderRepository.findOverdueOrders(SENT, EXECUTED))
        .willReturn(
            List.of(
                TransactionOrder.builder()
                    .id(99L)
                    .fund(TKF100)
                    .instrumentIsin("IE000F60HVH9")
                    .instrumentType(ETF)
                    .transactionType(BUY)
                    .orderStatus(SENT)
                    .orderVenue(OrderVenue.SEB)
                    .orderTimestamp(null)
                    .orderUuid(UUID.randomUUID())
                    .build()));

    job().run();

    verify(emailService, never()).sendSystemEmail(org.mockito.ArgumentMatchers.any());
  }

  private static Instant dateOnly(int year, int month, int day) {
    return LocalDate.of(year, month, day).atTime(LocalTime.NOON).atZone(TALLINN).toInstant();
  }

  private static TransactionOrder order(
      long id,
      InstrumentType instrumentType,
      OrderStatus orderStatus,
      Instant orderTimestamp,
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
        .orderTimestamp(orderTimestamp)
        .orderUuid(UUID.randomUUID())
        .build();
  }
}
