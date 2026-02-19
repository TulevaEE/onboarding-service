package ee.tuleva.onboarding.investment.position.parser;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SebFundPositionParser implements FundPositionParser {

  private final Clock clock;

  private static final Set<String> FUND_CODES =
      Arrays.stream(TulevaFund.values()).map(TulevaFund::getCode).collect(Collectors.toSet());

  private static final Set<AccountType> UNIT_PRICE_ACCOUNT_TYPES =
      Set.of(AccountType.CASH, AccountType.LIABILITY, AccountType.RECEIVABLES);

  @Override
  public List<FundPosition> parse(List<Map<String, Object>> rawData, LocalDate reportDate) {
    return parse(rawData, reportDate, Map.of());
  }

  @Override
  public List<FundPosition> parse(
      List<Map<String, Object>> rawData, LocalDate reportDate, Map<String, Object> metadata) {
    LocalDate navDate = extractMetadataDate(metadata, "asOfDate");
    LocalDate sentDate = extractMetadataDate(metadata, "sentDate");

    if (navDate == null) {
      navDate = extractHeaderDate(rawData, "As of:");
    }
    if (sentDate == null) {
      sentDate = extractHeaderDate(rawData, "Sent:");
    }

    if (navDate == null) {
      log.warn("No 'As of' date found in SEB data, falling back to report date");
      navDate = reportDate;
    }
    if (sentDate == null) {
      log.warn("No 'Sent' date found in SEB data, falling back to report date");
      sentDate = reportDate;
    }

    LocalDate effectiveNavDate = navDate;
    LocalDate effectiveReportDate = sentDate;
    return rawData.stream()
        .map(row -> parseRow(row, effectiveNavDate, effectiveReportDate))
        .flatMap(Optional::stream)
        .toList();
  }

  private LocalDate extractMetadataDate(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(value.toString());
    } catch (Exception e) {
      log.warn("Failed to parse metadata date: key={}, value={}", key, value);
      return null;
    }
  }

  private LocalDate extractHeaderDate(List<Map<String, Object>> rawData, String headerLabel) {
    for (Map<String, Object> row : rawData) {
      String fundManagementColumn = getString(row, "Fund Management Company:");
      if (headerLabel.equals(fundManagementColumn)) {
        String dateValue = getString(row, "Tuleva Fondid AS");
        if (dateValue != null) {
          try {
            return LocalDate.parse(dateValue);
          } catch (Exception e) {
            log.warn("Failed to parse '{}' date: value={}", headerLabel, dateValue);
          }
        }
      }
    }
    return null;
  }

  private Optional<FundPosition> parseRow(
      Map<String, Object> row, LocalDate navDate, LocalDate reportDate) {
    try {
      String fundCode = extractFundCode(row);
      if (fundCode == null) {
        return Optional.empty();
      }

      String accountColumn = getString(row, "Account");
      if (isSkippableRow(accountColumn)) {
        return Optional.empty();
      }

      TulevaFund fund = TulevaFund.fromCode(fundCode);
      String accountName = getString(row, "Name");
      if (accountName == null || accountName.isBlank()) {
        return Optional.empty();
      }

      AccountType accountType = determineAccountType(accountName, accountColumn);
      BigDecimal marketPrice = parseMarketPrice(row, accountType);

      FundPosition position =
          FundPosition.builder()
              .navDate(navDate)
              .reportDate(reportDate)
              .fund(fund)
              .accountType(accountType)
              .accountName(accountName)
              .accountId(getString(row, "ISIN"))
              .quantity(getBigDecimal(row, "Quantity"))
              .marketPrice(marketPrice)
              .currency(getString(row, "Currency"))
              .marketValue(getBigDecimal(row, "Market Value (EUR)"))
              .createdAt(Instant.now(clock))
              .build();

      return Optional.of(position);
    } catch (Exception e) {
      log.debug("Skipping non-data row: row={}", row);
      return Optional.empty();
    }
  }

  private String extractFundCode(Map<String, Object> row) {
    String clientName = getString(row, "Client name");
    if (clientName != null && FUND_CODES.contains(clientName)) {
      return clientName;
    }
    return row.values().stream()
        .map(this::toString)
        .filter(value -> value != null && FUND_CODES.contains(value))
        .findFirst()
        .orElse(null);
  }

  private boolean isSkippableRow(String accountColumn) {
    if (accountColumn == null) {
      return false;
    }
    return "Total".equalsIgnoreCase(accountColumn.trim());
  }

  private AccountType determineAccountType(String accountName, String accountColumn) {
    String nameLower = accountName.toLowerCase();

    if (nameLower.contains("cash account")) {
      return AccountType.CASH;
    }
    if (nameLower.contains("receivable")) {
      return AccountType.RECEIVABLES;
    }
    if (nameLower.contains("payable")) {
      return AccountType.LIABILITY;
    }
    if ("Register".equalsIgnoreCase(accountColumn)
        || nameLower.startsWith("total outstanding units")) {
      return AccountType.UNITS;
    }
    return AccountType.SECURITY;
  }

  private String getString(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return toString(value);
  }

  private String toString(Object value) {
    if (value == null) {
      return null;
    }
    String str = value.toString().trim();
    return str.isEmpty() ? null : str;
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
    int lastDot = normalized.lastIndexOf('.');
    if (lastDot > 0) {
      String beforeLastDot = normalized.substring(0, lastDot).replace(".", "");
      normalized = beforeLastDot + "." + normalized.substring(lastDot + 1);
    }
    try {
      return new BigDecimal(normalized);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private BigDecimal parseMarketPrice(Map<String, Object> row, AccountType accountType) {
    BigDecimal price = getBigDecimal(row, "Market price");
    boolean priceIsNullOrZero = price == null || price.compareTo(ZERO) == 0;
    if (priceIsNullOrZero && UNIT_PRICE_ACCOUNT_TYPES.contains(accountType)) {
      return ONE;
    }
    return price;
  }
}
