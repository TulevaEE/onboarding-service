package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionCalculationService {

  private final FundPositionRepository fundPositionRepository;
  private final PositionPriceResolver priceResolver;

  public List<PositionCalculation> calculate(TulevaFund fund, LocalDate date) {
    List<FundPosition> positions =
        fundPositionRepository.findByReportingDateAndFundCodeAndAccountType(
            date, fund.getCode(), SECURITY);

    log.info(
        "Calculating positions: fund={}, date={}, positionCount={}", fund, date, positions.size());

    return positions.stream()
        .filter(position -> position.getAccountId() != null)
        .map(position -> calculatePosition(position, fund, date))
        .filter(Objects::nonNull)
        .toList();
  }

  public List<PositionCalculation> calculate(List<TulevaFund> funds, LocalDate date) {
    return funds.stream().flatMap(fund -> calculate(fund, date).stream()).toList();
  }

  public List<PositionCalculation> calculateForLatestDate(List<TulevaFund> funds) {
    return funds.stream().flatMap(fund -> calculateForLatestDate(fund).stream()).toList();
  }

  public List<PositionCalculation> calculateForLatestDate(TulevaFund fund) {
    Optional<LocalDate> latestDate =
        fundPositionRepository.findLatestReportingDateByFundCode(fund.getCode());

    if (latestDate.isEmpty()) {
      log.warn("No fund positions found: fund={}", fund);
      return List.of();
    }

    return calculate(fund, latestDate.get());
  }

  private PositionCalculation calculatePosition(
      FundPosition position, TulevaFund fund, LocalDate date) {
    String isin = position.getAccountId();

    Optional<ResolvedPrice> resolvedPriceOpt = priceResolver.resolve(isin, date);

    if (resolvedPriceOpt.isEmpty()) {
      log.warn("No ticker found for ISIN: isin={}, fund={}", isin, fund);
      return null;
    }

    ResolvedPrice resolvedPrice = resolvedPriceOpt.get();
    BigDecimal quantity = position.getQuantity();
    BigDecimal calculatedMarketValue = calculateMarketValue(quantity, resolvedPrice.usedPrice());

    return PositionCalculation.builder()
        .isin(isin)
        .fund(fund)
        .date(date)
        .quantity(quantity)
        .eodhdPrice(resolvedPrice.eodhdPrice())
        .yahooPrice(resolvedPrice.yahooPrice())
        .usedPrice(resolvedPrice.usedPrice())
        .priceSource(resolvedPrice.priceSource())
        .calculatedMarketValue(calculatedMarketValue)
        .validationStatus(resolvedPrice.validationStatus())
        .priceDiscrepancyPercent(resolvedPrice.discrepancyPercent())
        .priceDate(resolvedPrice.priceDate())
        .createdAt(Instant.now())
        .build();
  }

  private BigDecimal calculateMarketValue(BigDecimal quantity, BigDecimal price) {
    if (quantity == null || price == null) {
      return null;
    }
    return quantity.multiply(price);
  }
}
