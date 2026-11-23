package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SebReportedPrecision.asSebReportsIt;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisRebuildFromBaselineRequested;
import ee.tuleva.onboarding.investment.event.trigger.JobTriggerQueue;
import ee.tuleva.onboarding.investment.transaction.BankDating;
import ee.tuleva.onboarding.investment.transaction.PreviousSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class OrderBankSettlement {

  static final String MODIFIED_BY_BANK_SETTLEMENT = "system:bank-settlement";
  private static final int EXECUTED_QUANTITY_COLUMN_SCALE = 4;

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementService settlementService;
  private final QuantityAmountValidator quantityAmountValidator;
  private final ReconciliationAuditRecorder auditRecorder;
  private final JobTriggerQueue jobTriggerQueue;

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
        bookedPieces.stream().map(BookedPiece::bookingDate).max(naturalOrder()).orElseThrow();
    if (!listing.settlesOn(bookingDate, order, executions)) {
      return BankSettlementOutcome.NO_BANK_DATE;
    }
    bookedPieces.forEach(
        piece -> {
          piece.execution().setNetSettlementAmount(piece.cash().abs());
          executionRepository.save(piece.execution());
        });
    return switch (settlementService.settleFromTheBank(order, bookingDate)) {
      case BankDating.Settled() -> {
        takeTheBanksUnitsWhereSebRoundedThem(order, bookedPieces);
        auditRecorder.recordSettlementFromBank(order, bookingDate);
        yield BankSettlementOutcome.SETTLED;
      }
      case BankDating.AlreadyByTheBank() -> BankSettlementOutcome.ALREADY_FROM_BANK;
      case BankDating.Redated(PreviousSettlement previous) -> {
        takeTheBanksUnitsWhereSebRoundedThem(order, bookedPieces);
        auditRecorder.recordSettlementRedatedByBank(order, bookingDate, previous);
        yield previous.reportDate().equals(bookingDate)
            ? BankSettlementOutcome.SAME_DATE
            : BankSettlementOutcome.NEW_DATE;
      }
    };
  }

  private void takeTheBanksUnitsWhereSebRoundedThem(
      TransactionOrder order, List<BookedPiece> pieces) {
    if (!pieces.stream().allMatch(BookedPiece::unitsAgreeWithTheBanksToSebsPrecision)) {
      return;
    }
    List<BookedPiece> roundedBySeb =
        pieces.stream().filter(BookedPiece::unitsDifferFromTheBanks).toList();
    if (roundedBySeb.isEmpty()) {
      return;
    }
    roundedBySeb.forEach(piece -> takeTheBanksUnits(order, piece));
    jobTriggerQueue.defer(RunPortfolioCostBasisRebuildFromBaselineRequested.JOB_NAME);
  }

  private void takeTheBanksUnits(TransactionOrder order, BookedPiece piece) {
    BigDecimal sebUnits = requireNonNull(piece.execution().getExecutedQuantity());
    BigDecimal bankUnits = requireNonNull(piece.bankUnits());
    piece.execution().setExecutedQuantity(bankUnits);
    piece.execution().setModifiedBy(MODIFIED_BY_BANK_SETTLEMENT);
    executionRepository.save(piece.execution());
    auditRecorder.recordUnitsFromBank(
        order, piece.execution(), piece.bookingDate(), sebUnits, bankUnits);
  }

  private static Optional<BookedPiece> bookedPiece(
      TransactionOrder order, TransactionExecution execution, BookingsByReference bookings) {
    Optional<TradeSettlementBooking> found = bookings.booking(execution.getBrokerTransactionId());
    if (found.isEmpty()) {
      return Optional.empty();
    }
    TradeSettlementBooking booking = found.get();
    BigDecimal cash = booking.cashAmount();
    if (cash == null) {
      throw new IllegalStateException(
          "Bank booking has no trade cash leg: orderId="
              + order.getId()
              + ", brokerReference="
              + execution.getBrokerTransactionId());
    }
    if (!BookingsByReference.fits(order, booking)) {
      log.warn(
          "Bank booking does not fit its order: orderId={}, brokerReference={}, orderFund={},"
              + " bookingFund={}, orderIsin={}, bookingIsin={}, side={}, cash={}",
          order.getId(),
          execution.getBrokerTransactionId(),
          order.getFund(),
          booking.fund(),
          order.getInstrumentIsin(),
          booking.isin(),
          order.getTransactionType(),
          cash);
      return Optional.empty();
    }
    return Optional.of(new BookedPiece(execution, booking.bookingDate(), cash, booking.units()));
  }

  private record BookedPiece(
      TransactionExecution execution,
      LocalDate bookingDate,
      BigDecimal cash,
      @Nullable BigDecimal units) {

    @Nullable BigDecimal bankUnits() {
      BigDecimal custodyUnits = units;
      return custodyUnits == null ? null : atTheRegistrysScale(custodyUnits.abs());
    }

    boolean unitsAgreeWithTheBanksToSebsPrecision() {
      BigDecimal sebUnits = execution.getExecutedQuantity();
      BigDecimal custodyUnits = units;
      if (sebUnits == null || custodyUnits == null) {
        return false;
      }
      BigDecimal exactUnits = custodyUnits.abs();
      return sebUnits.compareTo(atTheRegistrysScale(exactUnits)) == 0
          || sebUnits.compareTo(asSebReportsIt(exactUnits)) == 0;
    }

    boolean unitsDifferFromTheBanks() {
      BigDecimal sebUnits = execution.getExecutedQuantity();
      BigDecimal bankUnits = bankUnits();
      return sebUnits != null && bankUnits != null && sebUnits.compareTo(bankUnits) != 0;
    }

    private static BigDecimal atTheRegistrysScale(BigDecimal quantity) {
      return quantity.setScale(EXECUTED_QUANTITY_COLUMN_SCALE, HALF_UP);
    }
  }
}
