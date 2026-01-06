package ee.tuleva.onboarding.investment.position;

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
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FundPositionCsvParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final String DELIMITER = ";";

  private static final Set<String> ALLOWED_ASSET_TYPES =
      Set.of("Equities", "Fixed Income", "Cash & Cash Equiv", "Cash");

  private static final int COL_REPORT_DATE = 0;
  private static final int COL_NAV_DATE = 1;
  private static final int COL_PORTFOLIO = 2;
  private static final int COL_ASSET_TYPE = 3;
  private static final int COL_FUND_CURR = 4;
  private static final int COL_ISIN = 5;
  private static final int COL_ASSET_NAME = 6;
  private static final int COL_QUANTITY = 7;
  private static final int COL_ASSET_CURR = 8;
  private static final int COL_PRICE_PC = 9;
  private static final int COL_MARKET_VALUE_PC = 15;
  private static final int COL_INSTRUMENT_TYPE = 24;
  private static final int COL_PRCT_NAV = 25;
  private static final int COL_ISSUER_NAME = 26;
  private static final int COL_SECURITY_ID = 31;

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

      if (columns.length < COL_SECURITY_ID + 1) {
        log.warn("Skipping line with insufficient columns: columnCount={}", columns.length);
        return Optional.empty();
      }

      String assetType = columns[COL_ASSET_TYPE].trim();
      if (!ALLOWED_ASSET_TYPES.contains(assetType)) {
        return Optional.empty();
      }

      String portfolio = columns[COL_PORTFOLIO].trim();
      String fundCode = TulevaFund.normalize(portfolio);

      FundPosition position =
          FundPosition.builder()
              .reportDate(parseDate(columns[COL_REPORT_DATE]))
              .navDate(parseDate(columns[COL_NAV_DATE]))
              .portfolio(portfolio)
              .fundCode(fundCode)
              .assetType(assetType)
              .instrumentType(parseString(columns[COL_INSTRUMENT_TYPE]))
              .isin(parseString(columns[COL_ISIN]))
              .securityId(parseString(columns[COL_SECURITY_ID]))
              .assetName(columns[COL_ASSET_NAME].trim())
              .issuerName(parseString(columns[COL_ISSUER_NAME]))
              .quantity(parseBigDecimal(columns[COL_QUANTITY]))
              .fundCurrency(parseString(columns[COL_FUND_CURR]))
              .assetCurrency(parseString(columns[COL_ASSET_CURR]))
              .price(parseBigDecimal(columns[COL_PRICE_PC]))
              .marketValue(parseBigDecimal(columns[COL_MARKET_VALUE_PC]))
              .percentageOfNav(parseBigDecimal(columns[COL_PRCT_NAV]))
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
    String normalized = trimmed.replace(" ", "").replace(",", ".");
    return new BigDecimal(normalized);
  }
}
