package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.AgainstTheReportRows;
import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.CarriedByAPendingFill;
import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Unmatched;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    List<TradeSettlementBooking> bookings =
        tradeSettlementBookings
            .bookedFor(window.fund(), window.reportDate().plusDays(1), window.today())
            .stream()
            .filter(booking -> booking.cashAmount() != null)
            .filter(booking -> window.holds(booking.externalReference(), booking.bookingDate()))
            .toList();
    Map<String, List<Fill>> fillsByReference = fillsByReference(bookings);
    List<Resolution> resolutions =
        bookings.stream()
            .map(
                booking ->
                    new Resolution(
                        booking,
                        onlyFittingFill(
                            booking,
                            fillsByReference.getOrDefault(booking.brokerReference(), List.of()))))
            .toList();
    Map<String, Long> bookingsPerReference =
        bookings.stream()
            .map(TradeSettlementBooking::brokerReference)
            .filter(Objects::nonNull)
            .collect(groupingBy(identity(), counting()));
    Map<Long, Long> bookingsPerExecution =
        resolutions.stream()
            .flatMap(resolution -> resolution.fill().stream())
            .collect(groupingBy(fill -> fill.execution().getId(), counting()));
    return new TradeSettlementsInTheBalance(
        resolutions.stream()
            .map(
                resolution ->
                    resolution
                        .fill()
                        .filter(
                            fill ->
                                bookingsPerReference.getOrDefault(
                                            resolution.booking().brokerReference(), 0L)
                                        == 1
                                    && bookingsPerExecution.getOrDefault(
                                            fill.execution().getId(), 0L)
                                        == 1)
                        .map(fill -> settled(resolution.booking(), fill, window))
                        .orElseGet(() -> new Unmatched(resolution.booking())))
            .toList());
  }

  private record Resolution(TradeSettlementBooking booking, Optional<Fill> fill) {}

  private Map<String, List<Fill>> fillsByReference(List<TradeSettlementBooking> bookings) {
    Set<String> references =
        bookings.stream()
            .map(TradeSettlementBooking::brokerReference)
            .filter(Objects::nonNull)
            .collect(toUnmodifiableSet());
    if (references.isEmpty()) {
      return Map.of();
    }
    List<TransactionExecution> executions =
        executionRepository.findAllByBrokerTransactionIdIn(references);
    Map<Long, TransactionOrder> ordersById =
        orderRepository
            .findAllById(executions.stream().map(TransactionExecution::getOrderId).toList())
            .stream()
            .collect(toMap(TransactionOrder::getId, identity()));
    return executions.stream()
        .flatMap(
            execution ->
                Optional.ofNullable(ordersById.get(execution.getOrderId()))
                    .map(order -> new Fill(order, execution))
                    .stream())
        .collect(
            groupingBy(
                fill -> Objects.requireNonNull(fill.execution().getBrokerTransactionId()),
                toList()));
  }

  private static Optional<Fill> onlyFittingFill(
      TradeSettlementBooking booking, List<Fill> candidates) {
    List<Fill> fitting = candidates.stream().filter(fill -> fill.fits(booking)).toList();
    return fitting.size() == 1 ? Optional.of(fitting.getFirst()) : Optional.empty();
  }

  private static SettledInTheBalance settled(
      TradeSettlementBooking booking, Fill fill, NettingWindow window) {
    return carriedByTheOrder(fill, window)
        ? new CarriedByAPendingFill(booking, fill.execution().getId())
        : new AgainstTheReportRows(booking);
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
