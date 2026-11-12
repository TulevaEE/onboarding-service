package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.CashKnownToArrive.investableBeforeItArrives;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.transaction.UnitReceivable.Counted.BEFORE_IT_ARRIVES;
import static ee.tuleva.onboarding.investment.transaction.UnitReceivable.Counted.ONCE_IT_ARRIVES;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.CashKnownToArrive.Calculation;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.RegistrarCashFlowRepository;
import ee.tuleva.onboarding.ledger.RegistrarContribution;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class UnitReceivableReader {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

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
        whenCounted(new Calculation(asOfDate, LocalDate.now(clock.withZone(TALLINN)))));
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

  private static UnitReceivable.Counted whenCounted(Calculation calculation) {
    return investableBeforeItArrives(calculation) ? BEFORE_IT_ARRIVES : ONCE_IT_ARRIVES;
  }
}
