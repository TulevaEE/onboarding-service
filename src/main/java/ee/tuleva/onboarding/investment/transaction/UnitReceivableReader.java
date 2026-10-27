package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.RegistrarCashFlowRepository;
import ee.tuleva.onboarding.ledger.RegistrarContribution;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class UnitReceivableReader {

  private static final Set<TulevaFund> SISEKORD_4_P_2_5_3_PENSION_FUNDS = Set.of(TUV100);
  private static final LocalDate SISEKORD_4_P_2_5_3_EVERY_FUND_FROM = LocalDate.of(2027, 1, 1);

  private final FundPositionRepository fundPositionRepository;
  private final RegistrarCashFlowRepository registrarCashFlows;
  private final Clock clock;

  UnitReceivable read(TulevaFund fund, LocalDate positionDate, LocalDate asOfDate) {
    if (fund.isSavingsFund()) {
      return UnitReceivable.NONE;
    }
    return new UnitReceivable(
        reported(fund, positionDate),
        receivedAfter(fund, positionDate, asOfDate),
        mayInvestCashKnownToArrive(fund, earlierOf(asOfDate, LocalDate.now(clock))));
  }

  private BigDecimal reported(TulevaFund fund, LocalDate positionDate) {
    return fundPositionRepository
        .findByNavDateAndFundAndAccountType(positionDate, fund, RECEIVABLES)
        .stream()
        .filter(position -> position.isUnitReceivableOf(fund))
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal receivedAfter(TulevaFund fund, LocalDate positionDate, LocalDate asOfDate) {
    return registrarCashFlows.findContributions(fund, positionDate.plusDays(1), asOfDate).stream()
        .map(RegistrarContribution::amount)
        .reduce(ZERO, BigDecimal::add);
  }

  private static boolean mayInvestCashKnownToArrive(TulevaFund fund, LocalDate tradeDay) {
    return SISEKORD_4_P_2_5_3_PENSION_FUNDS.contains(fund)
        || !tradeDay.isBefore(SISEKORD_4_P_2_5_3_EVERY_FUND_FROM);
  }

  private static LocalDate earlierOf(LocalDate one, LocalDate other) {
    return one.isBefore(other) ? one : other;
  }
}
