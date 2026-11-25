package ee.tuleva.onboarding.investment.transaction;

import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingIndexLevels;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoFundPriceOnPositionDate;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexAdjustedPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexMove;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class IndexAdjustedFundPrices {

  private static final Map<String, String> SCREENED_INDEX_BY_BENCHMARK_CATEGORY =
      Map.of(
          "EQUITY_DM", "MSCI_WORLD_SCREENED",
          "EQUITY_EM", "MSCI_EM_EX_SELECT_CONTROVERSIES");
  private static final int ADJUSTED_PRICE_DECIMALS = 6;
  private static final String MUTUAL_FUND = "FUND";

  private final InstrumentReferenceService instrumentReferenceService;
  private final PositionPriceResolver positionPriceResolver;
  private final FundValueProvider fundValueProvider;

  record Adjusted(
      Map<String, IndexAdjustedPrice> prices,
      List<MissingIndexLevel> missingIndexLevels,
      List<String> fundsWithoutPriceOnPositionDate) {

    static final Adjusted NONE = new Adjusted(Map.of(), List.of(), List.of());

    List<InputGateFailure> failures(LocalDate positionDate) {
      List<InputGateFailure> failures = new ArrayList<>();
      if (!fundsWithoutPriceOnPositionDate.isEmpty()) {
        failures.add(new NoFundPriceOnPositionDate(fundsWithoutPriceOnPositionDate, positionDate));
      }
      if (!missingIndexLevels.isEmpty()) {
        failures.add(new MissingIndexLevels(missingIndexLevels));
      }
      return List.copyOf(failures);
    }

    Map<String, TradePrepPrice> over(Map<String, TradePrepPrice> otherPrices) {
      Map<String, TradePrepPrice> merged = new HashMap<>(otherPrices);
      merged.putAll(prices);
      return Map.copyOf(merged);
    }

    private static Adjusted priced(String isin, IndexAdjustedPrice price) {
      return new Adjusted(Map.of(isin, price), List.of(), List.of());
    }

    private static Adjusted withoutPrice(String isin) {
      return new Adjusted(Map.of(), List.of(), List.of(isin));
    }

    private static Adjusted combined(List<Adjusted> funds) {
      return new Adjusted(
          funds.stream()
              .flatMap(fund -> fund.prices().entrySet().stream())
              .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)),
          funds.stream().flatMap(fund -> fund.missingIndexLevels().stream()).toList(),
          funds.stream().flatMap(fund -> fund.fundsWithoutPriceOnPositionDate().stream()).toList());
    }
  }

  Adjusted onPositionDate(Collection<String> isins, LocalDate positionDate) {
    return Adjusted.combined(
        isins.stream()
            .sorted()
            .flatMap(
                isin ->
                    screenedIndexOf(isin)
                        .map(indexKey -> onPositionDate(isin, indexKey, positionDate))
                        .stream())
            .toList());
  }

  private Adjusted onPositionDate(String isin, String indexKey, LocalDate positionDate) {
    return fundPriceOnOrBefore(isin, positionDate)
        .map(fundPrice -> onPositionDate(isin, indexKey, fundPrice, positionDate))
        .orElseGet(() -> Adjusted.withoutPrice(isin));
  }

  private Adjusted onPositionDate(
      String isin, String indexKey, ResolvedPrice fundPrice, LocalDate positionDate) {
    BigDecimal nav = Objects.requireNonNull(fundPrice.usedPrice());
    LocalDate navDate = Objects.requireNonNull(fundPrice.priceDate());
    if (navDate.equals(positionDate)) {
      return Adjusted.priced(isin, new IndexAdjustedPrice(fundPrice, positionDate, null, nav));
    }
    Optional<BigDecimal> onNavDate = level(indexKey, navDate);
    Optional<BigDecimal> onPositionDate = level(indexKey, positionDate);
    if (onNavDate.isPresent() && onPositionDate.isPresent()) {
      IndexMove move = new IndexMove(indexKey, onNavDate.get(), onPositionDate.get());
      return Adjusted.priced(
          isin, new IndexAdjustedPrice(fundPrice, positionDate, move, moved(nav, move)));
    }
    List<MissingIndexLevel> missing = new ArrayList<>();
    if (onNavDate.isEmpty()) {
      missing.add(new MissingIndexLevel(isin, indexKey, navDate));
    }
    if (onPositionDate.isEmpty()) {
      missing.add(new MissingIndexLevel(isin, indexKey, positionDate));
    }
    return new Adjusted(Map.of(), List.copyOf(missing), List.of());
  }

  private static BigDecimal moved(BigDecimal nav, IndexMove move) {
    return nav.multiply(move.levelOnPositionDate())
        .divide(move.levelOnFundPriceDate(), ADJUSTED_PRICE_DECIMALS, HALF_UP);
  }

  private Optional<String> screenedIndexOf(String isin) {
    return instrumentReferenceService
        .findByIsin(isin)
        .filter(instrument -> MUTUAL_FUND.equals(instrument.getInstrumentType()))
        .map(InstrumentReference::getBenchmarkCategory)
        .map(SCREENED_INDEX_BY_BENCHMARK_CATEGORY::get);
  }

  private Optional<ResolvedPrice> fundPriceOnOrBefore(String isin, LocalDate positionDate) {
    return positionPriceResolver
        .resolve(isin, positionDate)
        .filter(price -> price.usedPrice() != null && price.usedPrice().signum() > 0)
        .filter(price -> price.priceDate() != null);
  }

  private Optional<BigDecimal> level(String indexKey, LocalDate date) {
    return fundValueProvider
        .getValueForDate(indexKey, date)
        .map(FundValue::value)
        .filter(value -> value.signum() > 0);
  }
}
