package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.investment.transaction.BankDating;
import ee.tuleva.onboarding.investment.transaction.PreviousSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class OrderBankSettlement {

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementService settlementService;
  private final QuantityAmountValidator quantityAmountValidator;
  private final ReconciliationAuditRecorder auditRecorder;

  @Transactional
  BankSettlementOutcome settle(
      Long orderId,
      BookingsByReference bookings,
      PendingListing listing,
      TransactionMatchingProperties matchingProperties) {
    TransactionOrder order = orderRepository.findById(orderId).orElseThrow();
    if (settlementService.isSettledByTheBank(order)) {
      return BankSettlementOutcome.ALREADY_FROM_BANK;
    }
    List<TransactionExecution> executions = executionRepository.findAllByOrderId(orderId);
    List<BookedPiece> bookedPieces =
        executions.stream()
            .flatMap(execution -> bookedPiece(order, execution, bookings).stream())
            .toList();
    if (executions.isEmpty()
        || bookedPieces.size() < executions.size()
        || quantityAmountValidator.isShortFill(order, executions, matchingProperties)) {
      return BankSettlementOutcome.NO_BANK_DATE;
    }
    LocalDate bookingDate =
        bookedPieces.stream()
            .map(piece -> piece.booking().bookingDate())
            .max(naturalOrder())
            .orElseThrow();
    if (!listing.settlesOn(bookingDate, order, executions)) {
      return BankSettlementOutcome.NO_BANK_DATE;
    }
    bookedPieces.forEach(
        piece -> {
          piece.execution().setNetSettlementAmount(piece.booking().cashAmount().abs());
          executionRepository.save(piece.execution());
        });
    return switch (settlementService.settleFromTheBank(order, bookingDate)) {
      case BankDating.Settled() -> {
        auditRecorder.recordSettlementFromBank(order, bookingDate);
        yield BankSettlementOutcome.SETTLED;
      }
      case BankDating.AlreadyByTheBank() -> BankSettlementOutcome.ALREADY_FROM_BANK;
      case BankDating.Redated(PreviousSettlement previous) -> {
        auditRecorder.recordSettlementRedatedByBank(order, bookingDate, previous);
        yield previous.reportDate().equals(bookingDate)
            ? BankSettlementOutcome.SAME_DATE
            : BankSettlementOutcome.NEW_DATE;
      }
    };
  }

  private static Optional<BookedPiece> bookedPiece(
      TransactionOrder order, TransactionExecution execution, BookingsByReference bookings) {
    Optional<TradeSettlementBooking> found = bookings.booking(execution.getBrokerTransactionId());
    if (found.isEmpty()) {
      return Optional.empty();
    }
    TradeSettlementBooking booking = found.get();
    if (!order.getInstrumentIsin().equals(booking.isin())
        || cashLeftTheFund(booking) != (order.getTransactionType() == BUY)) {
      log.warn(
          "Bank booking does not fit its order: orderId={}, brokerReference={}, orderIsin={},"
              + " bookingIsin={}, side={}, cash={}",
          order.getId(),
          execution.getBrokerTransactionId(),
          order.getInstrumentIsin(),
          booking.isin(),
          order.getTransactionType(),
          booking.cashAmount());
      return Optional.empty();
    }
    return Optional.of(new BookedPiece(execution, booking));
  }

  private static boolean cashLeftTheFund(TradeSettlementBooking booking) {
    return booking.cashAmount().signum() < 0;
  }

  private record BookedPiece(TransactionExecution execution, TradeSettlementBooking booking) {}
}
