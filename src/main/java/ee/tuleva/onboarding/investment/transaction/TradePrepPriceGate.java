package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPrices;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradePrepPriceGate {

  private final FundPositionRepository fundPositionRepository;
  private final PositionAssembler positionAssembler;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final PositionPriceResolver positionPriceResolver;

  Optional<InputGateFailure> missingPrices(
      TulevaFund fund, LocalDate asOfDate, Set<String> excludedIsins) {
    List<String> unpriced =
        Stream.concat(heldIsins(fund, asOfDate), modelIsins(fund, asOfDate))
            .distinct()
            .sorted()
            .filter(isin -> !excludedIsins.contains(isin))
            .filter(isin -> !hasPrice(isin, asOfDate))
            .toList();
    if (unpriced.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new MissingPrices(unpriced));
  }

  private Stream<String> heldIsins(TulevaFund fund, LocalDate asOfDate) {
    return fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate).stream()
        .flatMap(
            positionDate ->
                positionAssembler.assemble(fund, positionDate, PendingOrderImpact.none()).stream())
        .filter(TradePrepPriceGate::isNotSoldOut)
        .map(PositionSnapshot::isin)
        .filter(Objects::nonNull);
  }

  private Stream<String> modelIsins(TulevaFund fund, LocalDate asOfDate) {
    return modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, asOfDate).stream()
        .filter(allocation -> allocation.getWeight().signum() > 0)
        .map(ModelPortfolioAllocation::getIsin)
        .filter(Objects::nonNull);
  }

  private static boolean isNotSoldOut(PositionSnapshot position) {
    BigDecimal quantity = position.quantity();
    return quantity == null || quantity.signum() != 0;
  }

  private boolean hasPrice(String isin, LocalDate asOfDate) {
    return positionPriceResolver
        .resolve(isin, asOfDate)
        .map(ResolvedPrice::usedPrice)
        .filter(price -> price.signum() > 0)
        .isPresent();
  }
}
