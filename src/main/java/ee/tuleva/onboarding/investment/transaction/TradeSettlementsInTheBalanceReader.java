package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;

import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.AgainstTheReportRows;
import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.CarriedByAPendingFill;
import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Unmatched;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class TradeSettlementsInTheBalanceReader {

  private final TradeSettlementBookings tradeSettlementBookings;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;

  TradeSettlementsInTheBalance read(NettingWindow window) {
    return new TradeSettlementsInTheBalance(
        tradeSettlementBookings
            .bookedFor(window.fund(), window.reportDate().plusDays(1), window.today())
            .stream()
            .filter(booking -> booking.cashAmount() != null)
            .filter(
                booking -> !window.pendingEntryReferences().contains(booking.externalReference()))
            .map(booking -> settled(booking, window))
            .toList());
  }

  private SettledInTheBalance settled(TradeSettlementBooking booking, NettingWindow window) {
    return Optional.ofNullable(booking.brokerReference())
        .flatMap(reference -> fillSettledBy(booking, reference))
        .<SettledInTheBalance>map(
            fill ->
                carriedByTheOrder(fill, window)
                    ? new CarriedByAPendingFill(booking, fill.execution().getId())
                    : new AgainstTheReportRows(booking))
        .orElseGet(() -> new Unmatched(booking));
  }

  private Optional<Fill> fillSettledBy(TradeSettlementBooking booking, String brokerReference) {
    List<Fill> fills =
        executionRepository.findAllByBrokerTransactionId(brokerReference).stream()
            .flatMap(
                execution ->
                    orderRepository.findById(execution.getOrderId()).stream()
                        .map(order -> new Fill(order, execution)))
            .filter(fill -> fill.fits(booking))
            .toList();
    return fills.size() == 1 ? Optional.of(fills.getFirst()) : Optional.empty();
  }

  private static boolean carriedByTheOrder(Fill fill, NettingWindow window) {
    return InPositionReport.ofFill(fill.order(), fill.execution(), window.reportDate())
            .cashToCount(fill.order(), BigDecimal.ONE)
            .signum()
        != 0;
  }

  private record Fill(TransactionOrder order, TransactionExecution execution) {

    boolean fits(TradeSettlementBooking booking) {
      BigDecimal cash = Objects.requireNonNull(booking.cashAmount());
      return order.getFund() == booking.fund()
          && order.getInstrumentIsin().equals(booking.isin())
          && cash.signum() < 0 == (order.getTransactionType() == BUY);
    }
  }
}
