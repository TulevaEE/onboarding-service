package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static java.time.ZoneOffset.UTC;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class SettlementFromBankService {

  private final TradeSettlementBookings tradeSettlementBookings;
  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementService settlementService;
  private final QuantityAmountValidator quantityAmountValidator;
  private final TransactionMatchingPolicy matchingPolicy;
  private final ReconciliationAuditRecorder auditRecorder;

  @Transactional
  void settleOrdersBookedSince(LocalDate since) {
    Map<String, TradeSettlementBooking> bookingsByReference =
        byReference(tradeSettlementBookings.bookedSince(since));
    TransactionMatchingProperties matchingProperties = matchingPolicy.current();
    orderRepository
        .findByOrderStatusInAndOrderTimestampSince(
            List.of(EXECUTED, SETTLED), since.atStartOfDay(UTC).toInstant())
        .forEach(order -> settle(order, bookingsByReference, matchingProperties));
  }

  private void settle(
      TransactionOrder order,
      Map<String, TradeSettlementBooking> bookingsByReference,
      TransactionMatchingProperties matchingProperties) {
    if (settlementService.isSettledByTheBank(order)) {
      return;
    }
    List<TransactionExecution> executions = executionRepository.findAllByOrderId(order.getId());
    List<BookedPiece> bookedPieces =
        executions.stream()
            .flatMap(execution -> bookedPiece(order, execution, bookingsByReference).stream())
            .toList();
    if (executions.isEmpty()
        || bookedPieces.size() < executions.size()
        || quantityAmountValidator.isShortFill(order, executions, matchingProperties)) {
      return;
    }
    bookedPieces.forEach(
        piece -> {
          piece.execution().setNetSettlementAmount(piece.booking().cashAmount().abs());
          executionRepository.save(piece.execution());
        });
    LocalDate bookingDate =
        bookedPieces.stream()
            .map(piece -> piece.booking().bookingDate())
            .max(naturalOrder())
            .orElseThrow();
    settlementService
        .settleFromTheBank(order, bookingDate)
        .ifPresentOrElse(
            previous -> auditRecorder.recordSettlementRedatedByBank(order, bookingDate, previous),
            () -> auditRecorder.recordSettlementFromBank(order, bookingDate));
  }

  private static Optional<BookedPiece> bookedPiece(
      TransactionOrder order,
      TransactionExecution execution,
      Map<String, TradeSettlementBooking> bookingsByReference) {
    String reference = execution.getBrokerTransactionId();
    TradeSettlementBooking booking = reference == null ? null : bookingsByReference.get(reference);
    if (booking == null) {
      return Optional.empty();
    }
    if (!order.getInstrumentIsin().equals(booking.isin())) {
      log.warn(
          "Bank booking is for another instrument than its order: orderId={}, brokerReference={},"
              + " orderIsin={}, bookingIsin={}",
          order.getId(),
          reference,
          order.getInstrumentIsin(),
          booking.isin());
      return Optional.empty();
    }
    return Optional.of(new BookedPiece(execution, booking));
  }

  private static Map<String, TradeSettlementBooking> byReference(
      List<TradeSettlementBooking> bookings) {
    Map<String, TradeSettlementBooking> byReference = new HashMap<>();
    for (TradeSettlementBooking booking : bookings) {
      String reference = booking.brokerReference();
      if (reference != null) {
        byReference.putIfAbsent(reference, booking);
      }
    }
    return byReference;
  }

  private record BookedPiece(TransactionExecution execution, TradeSettlementBooking booking) {}
}
