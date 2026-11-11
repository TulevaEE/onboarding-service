package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementFromBankServiceTest {

  private static final LocalDate SINCE = LocalDate.of(2026, 2, 1);
  private static final BooleanSupplier LOCK_HELD = () -> true;
  private static final PendingListing LISTING =
      new PendingListing(LocalDate.of(2026, 2, 20), Set.of(), Set.of(), Set.of());

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

    assertThat(service().settleOrdersBookedSince(SINCE, LOCK_HELD))
        .contains(new BankSettlementRun(1, 0, 0, 0, 1));
  }

  @Test
  void aRunThatLosesTheLock_stopsBeforeTheNextOrderAndSettlesNoMore() {
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(anyList(), any()))
        .willReturn(List.of(order(1L), order(2L)));
    given(latestPendingReport.listing()).willReturn(Optional.of(LISTING));
    given(tradeSettlementBookings.bookedSince(SINCE)).willReturn(List.of());
    given(orderBankSettlement.settle(eq(1L), any(), any(), any()))
        .willReturn(BankSettlementOutcome.SETTLED);
    Iterator<Boolean> heldBeforeEachOrder = List.of(true, false).iterator();

    assertThatThrownBy(() -> service().settleOrdersBookedSince(SINCE, heldBeforeEachOrder::next))
        .isInstanceOf(SettlementFromBankLockLostException.class);

    then(orderBankSettlement).should(never()).settle(eq(2L), any(), any(), any());
  }

  @Test
  void withoutAUsableSebPendingReport_nothingSettles() {
    given(latestPendingReport.listing()).willReturn(Optional.empty());

    assertThat(service().settleOrdersBookedSince(SINCE, LOCK_HELD)).isEmpty();
    then(orderBankSettlement).shouldHaveNoInteractions();
  }

  private static TransactionOrder order(Long id) {
    return TransactionOrder.builder().id(id).orderStatus(EXECUTED).build();
  }
}
