package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_PENSION;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.ONE_OFF_WITHDRAWAL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.epis.R16FlowCalculationService;
import ee.tuleva.onboarding.investment.epis.R16FundFlow;
import ee.tuleva.onboarding.investment.epis.R16PaymentDates;
import ee.tuleva.onboarding.investment.epis.R16Phase;
import ee.tuleva.onboarding.investment.epis.R16PhaseCalculator;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import ee.tuleva.onboarding.investment.portfolio.FundLimitRepository;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR16;
import ee.tuleva.onboarding.investment.transaction.R16Reserve.Basis;
import ee.tuleva.onboarding.investment.transaction.R16TypeForecast.Status;
import ee.tuleva.onboarding.ledger.RegistrarCashFlowRepository;
import ee.tuleva.onboarding.ledger.RegistrarPayout;
import ee.tuleva.onboarding.ledger.RegistrarPayoutReason;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class R16OutflowReader {

  private static final Set<TulevaFund> R16_FUNDS = Set.of(TUK75, TUK00, TUV100);

  private final R16FlowCalculationService r16FlowCalculationService;
  private final R16PhaseCalculator r16PhaseCalculator;
  private final RegistrarCashFlowRepository registrarCashFlows;
  private final FundLimitRepository fundLimitRepository;

  R16Reserve read(TulevaFund fund, LocalDate asOfDate, LocalDate bookedThrough) {
    if (!R16_FUNDS.contains(fund)) {
      return R16Reserve.NONE;
    }
    return r16FlowCalculationService
        .calculateThisMonthsFlows(fund, asOfDate)
        .map(flow -> reserve(flow, asOfDate, bookedThrough))
        .orElse(R16Reserve.NONE);
  }

  Optional<InputGateFailure> missingR16(TulevaFund fund, LocalDate asOfDate) {
    if (!R16_FUNDS.contains(fund) || !r16PhaseCalculator.knowsTradePrepPhaseOf(fund, asOfDate)) {
      return Optional.empty();
    }
    YearMonth paymentMonth = YearMonth.from(asOfDate);
    R16PaymentDates dates = r16FlowCalculationService.paymentDates(paymentMonth);
    if (!countsOutflow(r16PhaseCalculator.tradePrepPhaseFor(fund, dates, asOfDate))
        || r16FlowCalculationService.isLoaded(paymentMonth)) {
      return Optional.empty();
    }
    return Optional.of(new MissingR16(paymentMonth, dates.paymentDeadline(), dates.sellByDate()));
  }

  private R16Reserve reserve(R16FundFlow flow, LocalDate asOfDate, LocalDate bookedThrough) {
    Set<RegistrarPayoutReason> booked = typesBooked(flow, bookedThrough);
    List<R16TypeForecast> types =
        List.of(
            forecast(FUND_PENSION, flow.fondimaksedUnits(), flow.fondimaksedEur(), booked),
            forecast(ONE_OFF_WITHDRAWAL, flow.uhekordsedUnits(), flow.uhekordsedEur(), booked));
    Optional<FundLimit> limit = fundLimitRepository.findLatestByFundAsOf(flow.fund(), asOfDate);
    BigDecimal soft = limit.map(FundLimit::getReserveSoft).orElse(null);
    BigDecimal hard = limit.map(FundLimit::getReserveHard).orElse(null);
    if (r16PhaseCalculator.isCoveredByTheTradeDaysR45(flow.fund(), flow.paymentDates(), asOfDate)) {
      return new R16Reserve(Basis.COVERED_BY_R45, types, soft, hard, ZERO);
    }
    BigDecimal stillToPay =
        types.stream()
            .filter(R16TypeForecast::stillToPay)
            .map(R16TypeForecast::eur)
            .reduce(ZERO, BigDecimal::add);
    return new R16Reserve(
        Basis.FORECAST_NOT_YET_BOOKED,
        types,
        soft,
        hard,
        belowTheHardReserve(stillToPay, soft, hard));
  }

  private static BigDecimal belowTheHardReserve(
      BigDecimal stillToPay, @Nullable BigDecimal soft, @Nullable BigDecimal hard) {
    if (soft == null || hard == null) {
      return stillToPay;
    }
    return ZERO.max(stillToPay.subtract(soft.subtract(hard)));
  }

  private Set<RegistrarPayoutReason> typesBooked(R16FundFlow flow, LocalDate bookedThrough) {
    LocalDate monthEnd = YearMonth.from(flow.paymentMonth()).atEndOfMonth();
    LocalDate through = bookedThrough.isBefore(monthEnd) ? bookedThrough : monthEnd;
    if (through.isBefore(flow.sellByDate())) {
      return Set.of();
    }
    return registrarCashFlows.findPayouts(flow.fund(), flow.sellByDate(), through).stream()
        .map(RegistrarPayout::reason)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static R16TypeForecast forecast(
      RegistrarPayoutReason type,
      BigDecimal units,
      BigDecimal eur,
      Set<RegistrarPayoutReason> booked) {
    Status status =
        units.signum() <= 0
            ? Status.NOTHING_FORECAST
            : booked.contains(type) ? Status.BOOKED : Status.NOT_BOOKED;
    return new R16TypeForecast(type, units, eur, status);
  }

  private static boolean countsOutflow(R16Phase phase) {
    return switch (phase) {
      case ACTIVE, BUFFERED -> true;
      case IGNORE, VISIBLE -> false;
    };
  }
}
