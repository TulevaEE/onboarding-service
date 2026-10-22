package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class UnsettledTradeReader {

  private final FundPositionRepository fundPositionRepository;

  UnsettledTrades read(TulevaFund fund, LocalDate positionDate) {
    Stream<FundPosition> payables =
        fundPositionRepository
            .findByNavDateAndFundAndAccountType(positionDate, fund, LIABILITY)
            .stream()
            .filter(FundPosition::isTradePayable);
    Stream<FundPosition> receivables =
        fundPositionRepository
            .findByNavDateAndFundAndAccountType(positionDate, fund, RECEIVABLES)
            .stream()
            .filter(FundPosition::isTradeReceivable);
    return new UnsettledTrades(sumOf(payables).negate(), sumOf(receivables));
  }

  private static BigDecimal sumOf(Stream<FundPosition> positions) {
    return positions
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }
}
