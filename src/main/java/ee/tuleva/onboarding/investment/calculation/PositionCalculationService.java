package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

  private static final ZoneId TALLINN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundPositionRepository fundPositionRepository;
  private final PositionPriceResolver priceResolver;

  public List<PositionCalculation> calculate(TulevaFund fund, LocalDate date) {
    return calculate(fund, date, null);
  }

  public List<PositionCalculation> calculate(
      TulevaFund fund, LocalDate date, LocalTime cutoffTime) {
    List<FundPosition> positions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY);

    log.info(
        "Calculating positions: fund={}, date={}, positionCount={}", fund, date, positions.size());

    return positions.stream()
        .filter(position -> position.getAccountId() != null)
        .map(position -> calculatePosition(position, fund, date, cutoffTime))
        .filter(Objects::nonNull)
        .toList();
  }

  public List<PositionCalculation> calculate(List<TulevaFund> funds, LocalDate date) {
    return funds.stream().flatMap(fund -> calculate(fund, date).stream()).toList();
  }

  public List<PositionCalculation> calculateForLatestDate(
      List<TulevaFund> funds, LocalTime cutoffTime) {
    return funds.stream()
        .flatMap(fund -> calculateForLatestDate(fund, cutoffTime).stream())
        .toList();
  }

  public List<PositionCalculation> calculateForLatestDate(List<TulevaFund> funds) {
    return calculateForLatestDate(funds, null);
  }

  public List<PositionCalculation> calculateForLatestDate(TulevaFund fund) {
    return calculateForLatestDate(fund, null);
  }

  public List<PositionCalculation> calculateForLatestDate(TulevaFund fund, LocalTime cutoffTime) {
    Optional<LocalDate> latestDate = fundPositionRepository.findLatestNavDateByFund(fund);

    if (latestDate.isEmpty()) {
      log.warn("No fund positions found: fund={}", fund);
      return List.of();
    }

    return calculate(fund, latestDate.get(), cutoffTime);
  }

  private PositionCalculation calculatePosition(
      FundPosition position, TulevaFund fund, LocalDate date, LocalTime cutoffTime) {
    String isin = position.getAccountId();
    Instant updatedBefore = computeCutoff(position, cutoffTime);

    Optional<ResolvedPrice> resolvedPriceOpt = priceResolver.resolve(isin, date, updatedBefore);

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

  private Instant computeCutoff(FundPosition position, LocalTime cutoffTime) {
    if (cutoffTime == null) {
      return null;
    }
    LocalDate cutoffDate =
        position.getReportDate() != null
            ? position.getReportDate()
            : position.getNavDate().plusDays(1);
    return cutoffDate.atTime(cutoffTime).atZone(TALLINN_ZONE).toInstant();
  }

  private BigDecimal calculateMarketValue(BigDecimal quantity, BigDecimal price) {
    if (quantity == null || price == null) {
      return null;
    }
    return quantity.multiply(price);
  }
}
