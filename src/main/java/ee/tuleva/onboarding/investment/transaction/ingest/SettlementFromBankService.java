package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static java.time.ZoneOffset.UTC;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.investment.transaction.PreviousSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

  private enum Outcome {
    ALREADY_FROM_BANK,
    SETTLED,
    SAME_DATE,
    NEW_DATE,
    NO_BANK_DATE
  }

  @Transactional
  BankSettlementRun settleOrdersBookedSince(LocalDate since) {
    return settleOrdersBookedSince(since, Map.of());
  }

  @Transactional
  BankSettlementRun settleOrdersBookedSince(
      LocalDate since, Map<UUID, String> referencesOfOlderBookings) {
    BookingsByReference bookings =
        new BookingsByReference(
            tradeSettlementBookings.bookedSince(since), referencesOfOlderBookings);
    TransactionMatchingProperties matchingProperties = matchingPolicy.current();
    List<Outcome> outcomes =
        orderRepository
            .findByOrderStatusInAndOrderTimestampSince(
                List.of(EXECUTED, SETTLED), since.atStartOfDay(UTC).toInstant())
            .stream()
            .map(order -> settle(order, bookings, matchingProperties))
            .toList();
    return new BankSettlementRun(
        count(outcomes, Outcome.SETTLED),
        count(outcomes, Outcome.SAME_DATE),
        count(outcomes, Outcome.NEW_DATE),
        count(outcomes, Outcome.NO_BANK_DATE));
  }

  private Outcome settle(
      TransactionOrder order,
      BookingsByReference bookings,
      TransactionMatchingProperties matchingProperties) {
    if (settlementService.isSettledByTheBank(order)) {
      return Outcome.ALREADY_FROM_BANK;
    }
    List<TransactionExecution> executions = executionRepository.findAllByOrderId(order.getId());
    List<BookedPiece> bookedPieces =
        executions.stream()
            .flatMap(execution -> bookedPiece(order, execution, bookings).stream())
            .toList();
    if (executions.isEmpty()
        || bookedPieces.size() < executions.size()
        || quantityAmountValidator.isShortFill(order, executions, matchingProperties)) {
      return Outcome.NO_BANK_DATE;
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
    Optional<PreviousSettlement> previous = settlementService.settleFromTheBank(order, bookingDate);
    if (previous.isPresent()) {
      auditRecorder.recordSettlementRedatedByBank(order, bookingDate, previous.get());
      return previous.get().reportDate().equals(bookingDate) ? Outcome.SAME_DATE : Outcome.NEW_DATE;
    }
    auditRecorder.recordSettlementFromBank(order, bookingDate);
    return Outcome.SETTLED;
  }

  private static int count(List<Outcome> outcomes, Outcome outcome) {
    return (int) outcomes.stream().filter(outcome::equals).count();
  }

  private static Optional<BookedPiece> bookedPiece(
      TransactionOrder order, TransactionExecution execution, BookingsByReference bookings) {
    String reference = execution.getBrokerTransactionId();
    Optional<TradeSettlementBooking> found = bookings.booking(reference);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    TradeSettlementBooking booking = found.get();
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

  private record BookedPiece(TransactionExecution execution, TradeSettlementBooking booking) {}
}
