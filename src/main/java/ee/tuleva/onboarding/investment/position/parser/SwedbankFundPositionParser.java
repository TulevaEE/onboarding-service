package ee.tuleva.onboarding.investment.position.parser;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SwedbankFundPositionParser implements FundPositionParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final String DELIMITER = ";";

  private static final int COL_NAV_DATE = 1;
  private static final int COL_PORTFOLIO = 2;
  private static final int COL_ASSET_TYPE = 3;
  private static final int COL_ISIN = 5;
  private static final int COL_ASSET_NAME = 6;
  private static final int COL_QUANTITY = 7;
  private static final int COL_ASSET_CURRENCY = 8;
  private static final int COL_PRICE_PC = 9;
  private static final int COL_MARKET_VALUE_PC = 15;

  private static final int MIN_COLUMNS = 16;

  private static final Map<String, String> PORTFOLIO_TO_FUND_CODE =
      Map.of(
          "Tuleva Maailma Aktsiate Pensionifond", "TUK75",
          "Tuleva Maailma Võlakirjade Pensionifond", "TUK00",
          "Tuleva Maailma Volakirjade Pensionifond", "TUK00",
          "Tuleva Vabatahtlik Pensionifond", "TUV100",
          "Tuleva Vabatahtlik Pensionifon", "TUV100");

  private static final Map<String, AccountType> ASSET_TYPE_MAPPING =
      Map.of(
          "Equities", AccountType.SECURITY,
          "Fixed Income", AccountType.SECURITY,
          "Cash & Cash Equiv", AccountType.CASH,
          "Cash", AccountType.CASH,
          "Asset", AccountType.RECEIVABLES,
          "Liabilities", AccountType.LIABILITY,
          "TotalNetAsset", AccountType.NAV);

  @Override
  public List<FundPosition> parse(InputStream inputStream) {
    try (var reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      return reader.lines().skip(1).map(this::parseLine).flatMap(Optional::stream).toList();
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse fund position CSV", e);
    }
  }

  private Optional<FundPosition> parseLine(String line) {
    try {
      String[] columns = line.split(DELIMITER, -1);

      if (columns.length < MIN_COLUMNS) {
        log.warn("Skipping line with insufficient columns: columnCount={}", columns.length);
        return Optional.empty();
      }

      String assetType = columns[COL_ASSET_TYPE].trim();
      String portfolio = columns[COL_PORTFOLIO].trim();
      String fundCode = PORTFOLIO_TO_FUND_CODE.get(portfolio);
      if (fundCode == null) {
        log.warn("Unknown portfolio, skipping: portfolio={}", portfolio);
        return Optional.empty();
      }

      AccountType accountType = ASSET_TYPE_MAPPING.get(assetType);
      if (accountType == null) {
        log.warn("Unknown asset type, skipping: assetType={}", assetType);
        return Optional.empty();
      }

      FundPosition position =
          FundPosition.builder()
              .reportingDate(parseDate(columns[COL_NAV_DATE]))
              .fundCode(fundCode)
              .accountType(accountType)
              .accountName(columns[COL_ASSET_NAME].trim())
              .accountId(parseString(columns[COL_ISIN]))
              .quantity(parseBigDecimal(columns[COL_QUANTITY]))
              .marketPrice(parseBigDecimal(columns[COL_PRICE_PC]))
              .currency(parseString(columns[COL_ASSET_CURRENCY]))
              .marketValue(parseBigDecimal(columns[COL_MARKET_VALUE_PC]))
              .createdAt(Instant.now())
              .build();

      return Optional.of(position);
    } catch (Exception e) {
      log.error("Failed to parse line: line={}", line, e);
      return Optional.empty();
    }
  }

  private LocalDate parseDate(String value) {
    return LocalDate.parse(value.trim(), DATE_FORMAT);
  }

  private String parseString(String value) {
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private BigDecimal parseBigDecimal(String value) {
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String normalized = trimmed.replace(" ", "").replace(",", "");
    return new BigDecimal(normalized);
  }
}
