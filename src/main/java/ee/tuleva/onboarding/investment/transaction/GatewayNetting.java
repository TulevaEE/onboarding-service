package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.INFLOW;
import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.OUTFLOW;
import static ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Carrier.PENDING_ORDER;
import static ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Carrier.REPORT_ROWS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_SWITCH;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.SECOND_PILLAR_EXIT;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.SWITCH_TO_PENSION_INVESTMENT_ACCOUNT;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.THIRD_PILLAR_REDEMPTION;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.THIRD_PILLAR_SWITCH;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.epis.R45Row;
import ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction;
import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Carrier;
import ee.tuleva.onboarding.ledger.RegistrarCashFlowRepository;
import ee.tuleva.onboarding.ledger.RegistrarPayout;
import ee.tuleva.onboarding.ledger.RegistrarPayoutReason;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class GatewayNetting {

  private static final Set<RegistrarPayoutReason> R45_PAYOUTS =
      Set.of(THIRD_PILLAR_REDEMPTION, THIRD_PILLAR_SWITCH);
  private static final Set<RegistrarPayoutReason> PEVA_RAVA_PAYOUTS =
      Set.of(FUND_SWITCH, SWITCH_TO_PENSION_INVESTMENT_ACCOUNT, SECOND_PILLAR_EXIT);

  private final TradeSettlementBookings tradeSettlementBookings;
  private final RegistrarCashFlowRepository registrarCashFlows;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;

  TradeSettlementsInTheBalance tradeSettlements(NettingWindow window) {
    return new TradeSettlementsInTheBalance(
        tradeSettlementBookings
            .bookedFor(window.fund(), window.reportDate().plusDays(1), window.today())
            .stream()
            .filter(booking -> booking.cashAmount() != null)
            .filter(
                booking -> !window.pendingEntryReferences().contains(booking.externalReference()))
            .map(booking -> new SettledInTheBalance(booking, carrierOf(booking, window)))
            .toList());
  }

  Netted<UnsettledTrades> unsettledTrades(
      NettingWindow window,
      TradeSettlementsInTheBalance settlements,
      UnsettledTrades atReportDate) {
    List<BigDecimal> againstTheReportRows =
        settlements.settlements().stream()
            .filter(settled -> settled.carrier() == REPORT_ROWS)
            .map(settled -> Objects.requireNonNull(settled.booking().cashAmount()))
            .toList();
    BigDecimal paid = sumOf(againstTheReportRows, amount -> amount.signum() < 0).negate();
    BigDecimal received = sumOf(againstTheReportRows, amount -> amount.signum() > 0);
    return new Netted<>(
        atReportDate,
        new UnsettledTrades(
            ZERO.max(atReportDate.payables().subtract(paid)),
            ZERO.max(atReportDate.receivables().subtract(received))));
  }

  Netted<BigDecimal> pevaRava(NettingWindow window, PevaRavaForecast forecast) {
    BigDecimal stillCounted =
        forecast.cycle().execDate().isAfter(window.today())
            ? forecast.inForce()
            : forecast.inForceWithoutSwitchingInflow();
    BigDecimal paid =
        registrarCashFlows
            .findPayouts(window.fund(), forecast.cycle().lockDate(), window.today())
            .stream()
            .filter(payout -> PEVA_RAVA_PAYOUTS.contains(payout.reason()))
            .map(RegistrarPayout::amount)
            .reduce(ZERO, BigDecimal::add);
    return new Netted<>(forecast.inForce(), ZERO.max(stillCounted.subtract(paid)));
  }

  Netted<BigDecimal> r45(NettingWindow window, List<R45Row> rows) {
    LocalDate today = window.today();
    List<R45Row> notYetSettled = rows.stream().filter(row -> !settlesBefore(row, today)).toList();
    List<R45Row> later = rows.stream().filter(row -> settlesAfter(row, today)).toList();
    BigDecimal outflowDueToday =
        valueOf(rows.stream().filter(row -> today.equals(row.settlementDate())).toList(), OUTFLOW);
    BigDecimal paidToday =
        registrarCashFlows.findPayouts(window.fund(), today, today).stream()
            .filter(payout -> R45_PAYOUTS.contains(payout.reason()))
            .map(RegistrarPayout::amount)
            .reduce(ZERO, BigDecimal::add);
    return new Netted<>(
        outflowBeyondInflow(notYetSettled),
        ZERO.max(outflowDueToday.subtract(paidToday)).add(outflowBeyondInflow(later)));
  }

  private static boolean settlesBefore(R45Row row, LocalDate day) {
    LocalDate settlementDate = row.settlementDate();
    return settlementDate != null && settlementDate.isBefore(day);
  }

  private static boolean settlesAfter(R45Row row, LocalDate day) {
    LocalDate settlementDate = row.settlementDate();
    return settlementDate == null || settlementDate.isAfter(day);
  }

  private static BigDecimal outflowBeyondInflow(List<R45Row> rows) {
    return ZERO.max(valueOf(rows, OUTFLOW).subtract(valueOf(rows, INFLOW)));
  }

  private static BigDecimal valueOf(List<R45Row> rows, Direction direction) {
    return rows.stream()
        .filter(row -> row.direction() == direction)
        .map(R45Row::valueEur)
        .reduce(ZERO, BigDecimal::add);
  }

  private Carrier carrierOf(TradeSettlementBooking booking, NettingWindow window) {
    return Optional.ofNullable(booking.brokerReference())
        .flatMap(reference -> fillSettledBy(booking, reference))
        .filter(fill -> carriedByTheOrder(fill, window))
        .map(fill -> PENDING_ORDER)
        .orElse(REPORT_ROWS);
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

  private static BigDecimal sumOf(List<BigDecimal> amounts, Predicate<BigDecimal> side) {
    return amounts.stream().filter(side).reduce(ZERO, BigDecimal::add);
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
