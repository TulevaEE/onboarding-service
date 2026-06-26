package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionSettlementServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");
  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 5, 13);

  @Mock private TransactionSettlementRepository settlementRepository;
  @Mock private TransactionOrderRepository orderRepository;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private TransactionSettlementService service() {
    return new TransactionSettlementService(settlementRepository, orderRepository, clock);
  }

  @Test
  void recordSettlement_createsSettlementAndTransitionsOrderToSettled() {
    TransactionOrder order = order(42L);
    given(settlementRepository.findByOrderId(42L)).willReturn(Optional.empty());
    given(settlementRepository.save(any(TransactionSettlement.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    TransactionSettlement settlement = service().recordSettlement(order, REPORT_DATE);

    assertThat(settlement.getOrderId()).isEqualTo(42L);
    assertThat(settlement.getSettledAt()).isEqualTo(NOW);
    assertThat(settlement.getReportDate()).isEqualTo(REPORT_DATE);
    assertThat(order.getOrderStatus()).isEqualTo(SETTLED);
    verify(orderRepository).save(order);
  }

  @Test
  void recordSettlement_isIdempotentWhenSettlementAlreadyExists() {
    TransactionOrder order = order(42L);
    TransactionSettlement existing =
        TransactionSettlement.builder()
            .id(7L)
            .orderId(42L)
            .settledAt(Instant.parse("2026-05-12T09:00:00Z"))
            .reportDate(LocalDate.of(2026, 5, 12))
            .build();
    given(settlementRepository.findByOrderId(42L)).willReturn(Optional.of(existing));

    TransactionSettlement settlement = service().recordSettlement(order, REPORT_DATE);

    assertThat(settlement).isEqualTo(existing);
    assertThat(order.getOrderStatus()).isEqualTo(EXECUTED);
    verify(settlementRepository, never()).save(any());
    verify(orderRepository, never()).save(any());
  }

  private TransactionOrder order(Long id) {
    return TransactionOrder.builder()
        .id(id)
        .fund(TUK75)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderVenue(SEB)
        .orderStatus(EXECUTED)
        .build();
  }
}
