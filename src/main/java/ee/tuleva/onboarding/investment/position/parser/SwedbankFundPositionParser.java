package ee.tuleva.onboarding.investment.position.parser;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK00;
import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.TulevaFund.TUV100;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.TulevaFund;
import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SwedbankFundPositionParser implements FundPositionParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private static final Map<String, TulevaFund> PORTFOLIO_TO_FUND =
      Map.of(
          "Tuleva Maailma Aktsiate Pensionifond", TUK75,
          "Tuleva Maailma Võlakirjade Pensionifond", TUK00,
          "Tuleva Maailma Volakirjade Pensionifond", TUK00,
          "Tuleva Vabatahtlik Pensionifond", TUV100,
          "Tuleva Vabatahtlik Pensionifon", TUV100);

  private static final Map<String, AccountType> ASSET_TYPE_MAPPING =
      Map.of(
          "Equities", AccountType.SECURITY,
          "Fixed Income", AccountType.SECURITY,
          "Cash & Cash Equiv", AccountType.CASH,
          "Cash", AccountType.CASH,
          "Asset", AccountType.RECEIVABLES,
          "Liabilities", AccountType.LIABILITY,
          "TotalNetAsset", AccountType.NAV);

  private static final Set<AccountType> UNIT_PRICE_ACCOUNT_TYPES =
      Set.of(AccountType.CASH, AccountType.LIABILITY, AccountType.RECEIVABLES);

  @Override
  public List<FundPosition> parse(List<Map<String, Object>> rawData) {
    return rawData.stream().map(this::parseRow).flatMap(Optional::stream).toList();
  }

  private Optional<FundPosition> parseRow(Map<String, Object> row) {
    try {
      String portfolio = getString(row, "Portfolio");
      TulevaFund fund = PORTFOLIO_TO_FUND.get(portfolio);
      if (fund == null) {
        log.warn("Unknown portfolio, skipping: portfolio={}", portfolio);
        return Optional.empty();
      }

      String assetType = getString(row, "AssetType");
      AccountType accountType = ASSET_TYPE_MAPPING.get(assetType);
      if (accountType == null) {
        log.warn("Unknown asset type, skipping: assetType={}", assetType);
        return Optional.empty();
      }

      BigDecimal marketPrice = parseMarketPrice(row, accountType);

      FundPosition position =
          FundPosition.builder()
              .reportingDate(getDate(row, "NAVDate"))
              .fund(fund)
              .accountType(accountType)
              .accountName(getString(row, "AssetName"))
              .accountId(getString(row, "ISIN"))
              .quantity(getBigDecimal(row, "Quantity"))
              .marketPrice(marketPrice)
              .currency(getString(row, "AssetCurr"))
              .marketValue(getBigDecimal(row, "MarketValuePC"))
              .createdAt(Instant.now())
              .build();

      return Optional.of(position);
    } catch (Exception e) {
      log.error("Failed to parse row: row={}", row, e);
      return Optional.empty();
    }
  }

  private String getString(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value == null) {
      return null;
    }
    String str = value.toString().trim();
    return str.isEmpty() ? null : str;
  }

  private LocalDate getDate(Map<String, Object> row, String key) {
    String value = getString(row, key);
    return value != null ? LocalDate.parse(value, DATE_FORMAT) : null;
  }

  private BigDecimal getBigDecimal(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    String str = value.toString().trim();
    if (str.isEmpty()) {
      return null;
    }
    String normalized = str.replace(" ", "").replace(",", ".");
    return new BigDecimal(normalized);
  }

  private BigDecimal parseMarketPrice(Map<String, Object> row, AccountType accountType) {
    BigDecimal price = getBigDecimal(row, "PricePC");
    boolean priceIsNullOrZero = price == null || price.compareTo(ZERO) == 0;
    if (priceIsNullOrZero && UNIT_PRICE_ACCOUNT_TYPES.contains(accountType)) {
      return ONE;
    }
    return price;
  }
}
