package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementFromBankServiceTest {

  private static final LocalDate SINCE = LocalDate.of(2026, 2, 1);
  private static final PendingListing LISTING =
      new PendingListing(LocalDate.of(2026, 2, 20), Set.of(), Set.of());

  @Mock private TradeSettlementBookings tradeSettlementBookings;
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionMatchingPolicy matchingPolicy;
  @Mock private LatestPendingReport latestPendingReport;
  @Mock private OrderBankSettlement orderBankSettlement;

  private SettlementFromBankService service() {
    return new SettlementFromBankService(
        tradeSettlementBookings,
        orderRepository,
        matchingPolicy,
        latestPendingReport,
        orderBankSettlement);
  }

  @Test
  void anOrderThatFails_isCountedAndTheOthersStillSettle() {
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(anyList(), any()))
        .willReturn(List.of(order(1L), order(2L)));
    given(latestPendingReport.listing()).willReturn(Optional.of(LISTING));
    given(tradeSettlementBookings.bookedSince(SINCE)).willReturn(List.of());
    given(orderBankSettlement.settle(eq(1L), any(), any(), any()))
        .willThrow(new IllegalStateException("boom"));
    given(orderBankSettlement.settle(eq(2L), any(), any(), any()))
        .willReturn(BankSettlementOutcome.SETTLED);

    BankSettlementRun run = service().settleOrdersBookedSince(SINCE);

    assertThat(run).isEqualTo(new BankSettlementRun(1, 0, 0, 0, 1));
  }

  @Test
  void withoutACompleteSebPendingReport_nothingSettles() {
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(anyList(), any()))
        .willReturn(List.of(order(1L)));
    given(latestPendingReport.listing()).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().settleOrdersBookedSince(SINCE))
        .isInstanceOf(IllegalStateException.class);
  }

  private static TransactionOrder order(Long id) {
    return TransactionOrder.builder().id(id).orderStatus(EXECUTED).build();
  }
}
