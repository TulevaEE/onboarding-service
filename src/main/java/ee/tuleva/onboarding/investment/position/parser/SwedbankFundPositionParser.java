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
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SwedbankFundPositionParser implements FundPositionParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final String DELIMITER = "\t";

  private static final int COL_REPORTING_DATE = 0;
  private static final int COL_FUND_CODE = 1;
  private static final int COL_ACCOUNT_TYPE = 2;
  private static final int COL_ACCOUNT_NAME = 3;
  private static final int COL_ACCOUNT_ID = 4;
  private static final int COL_QUANTITY = 5;
  private static final int COL_MARKET_PRICE = 6;
  private static final int COL_CURRENCY = 7;
  private static final int COL_MARKET_VALUE = 8;

  private static final int EXPECTED_COLUMNS = 9;

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

      if (columns.length < EXPECTED_COLUMNS) {
        log.warn("Skipping line with insufficient columns: columnCount={}", columns.length);
        return Optional.empty();
      }

      FundPosition position =
          FundPosition.builder()
              .reportingDate(parseDate(columns[COL_REPORTING_DATE]))
              .fundCode(columns[COL_FUND_CODE].trim())
              .accountType(AccountType.valueOf(columns[COL_ACCOUNT_TYPE].trim()))
              .accountName(columns[COL_ACCOUNT_NAME].trim())
              .accountId(parseString(columns[COL_ACCOUNT_ID]))
              .quantity(parseBigDecimal(columns[COL_QUANTITY]))
              .marketPrice(parseBigDecimal(columns[COL_MARKET_PRICE]))
              .currency(parseString(columns[COL_CURRENCY]))
              .marketValue(parseBigDecimal(columns[COL_MARKET_VALUE]))
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
