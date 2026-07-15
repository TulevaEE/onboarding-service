package ee.tuleva.onboarding.investment.epis.parser;

import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.OUTFLOW;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findDate;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findValue;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.parseNumber;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.epis.R45TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class R45ReportParser {

  private static final String HEADER_MARKER = "Tehingu liik";
  private static final BigDecimal MAX_REASONABLE_VALUE = new BigDecimal("100000000");

  private final EpisCsvParser csvParser;

  public R45ParseResult parse(
      String csv, LocalDate today, Map<String, BigDecimal> fallbackNavByIsin) {
    EpisCsv parsed = csvParser.parse(csv, HEADER_MARKER);
    validateTehtudDate(parsed.preHeaderLines(), today);

    Map<String, BigDecimal> navByIsin = collectNavs(parsed.rows(), fallbackNavByIsin);
    Map<String, FlowAccumulator> flows = new LinkedHashMap<>();
    List<R45UnvaluedRow> unvaluedRows = new ArrayList<>();
    List<R45UnknownRow> unknownRows = new ArrayList<>();

    for (Map<String, String> row : parsed.rows()) {
      String typeCode = trimmedUpperCase(findValue(row, "tehingu liik"));
      String isin = normalizeIsin(findValue(row, "isin"));
      if (typeCode.isEmpty() || isin.isEmpty()) {
        if (!typeCode.isEmpty()) {
          log.warn("R45 row dropped: reason=missingIsin, typeCode={}", typeCode);
        }
        continue;
      }

      BigDecimal units = numberOrZero(row, "osakuid");
      BigDecimal amount = numberOrZero(row, "summa");
      validateMagnitude(units, amount);

      if (isSettledBefore(row, today)) {
        continue;
      }

      Optional<TulevaFund> fund = TulevaFund.findByIsin(isin);
      Optional<R45TransactionType> type = R45TransactionType.find(typeCode);
      if (fund.isEmpty() || type.isEmpty()) {
        String knownFundCode = fund.map(TulevaFund::getCode).orElse(null);
        unknownRows.add(new R45UnknownRow(typeCode, isin, knownFundCode));
        if (knownFundCode != null) {
          flows.computeIfAbsent(knownFundCode, code -> new FlowAccumulator());
        }
        continue;
      }
      String fundCode = fund.get().getCode();

      BigDecimal orderValue;
      if (amount.signum() != 0) {
        orderValue = amount.abs();
      } else if (units.signum() != 0) {
        BigDecimal nav = effectiveNav(row, navByIsin, isin);
        if (nav.signum() == 0) {
          unvaluedRows.add(new R45UnvaluedRow(fundCode, type.get(), units, isin));
          flows.computeIfAbsent(fundCode, code -> new FlowAccumulator());
          continue;
        }
        orderValue = units.multiply(nav).abs();
      } else {
        continue;
      }

      flows.computeIfAbsent(fundCode, code -> new FlowAccumulator()).add(type.get(), orderValue);
    }

    Map<String, R45Result> fundResults = new LinkedHashMap<>();
    flows.forEach((fundCode, accumulator) -> fundResults.put(fundCode, accumulator.toResult()));
    return new R45ParseResult(fundResults, List.copyOf(unvaluedRows), List.copyOf(unknownRows));
  }

  private static Map<String, BigDecimal> collectNavs(
      List<Map<String, String>> rows, Map<String, BigDecimal> fallbackNavByIsin) {
    Map<String, BigDecimal> navByIsin = new HashMap<>(fallbackNavByIsin);
    for (Map<String, String> row : rows) {
      String isin = normalizeIsin(findValue(row, "isin"));
      BigDecimal nav = numberOrZero(row, "nav");
      if (!isin.isEmpty() && nav.signum() > 0) {
        navByIsin.putIfAbsent(isin, nav);
      }
    }
    return navByIsin;
  }

  private static BigDecimal effectiveNav(
      Map<String, String> row, Map<String, BigDecimal> navByIsin, String isin) {
    BigDecimal rowNav = numberOrZero(row, "nav");
    if (rowNav.signum() > 0) {
      return rowNav;
    }
    return navByIsin.getOrDefault(isin, BigDecimal.ZERO);
  }

  private static boolean isSettledBefore(Map<String, String> row, LocalDate today) {
    String settlementValue = findValue(row, "täitmise kuupäev", "taitmise kuupaev");
    if (settlementValue == null) {
      return false;
    }
    LocalDate settlementDate = findDate(settlementValue);
    return settlementDate != null && settlementDate.isBefore(today);
  }

  private static void validateTehtudDate(List<String> preHeaderLines, LocalDate today) {
    LocalDate tehtud = findTehtudDate(preHeaderLines);
    if (tehtud == null) {
      throw new IllegalArgumentException(
          "R45 Tehtud date marker missing or unparseable: preHeaderLineCount="
              + preHeaderLines.size());
    }
    if (!tehtud.equals(today)) {
      throw new IllegalArgumentException(
          "R45 report is stale: tehtud=" + tehtud + ", today=" + today);
    }
  }

  @Nullable
  private static LocalDate findTehtudDate(List<String> preHeaderLines) {
    for (String line : preHeaderLines) {
      if (line.toLowerCase(Locale.ROOT).contains("tehtud")) {
        return findDate(line);
      }
    }
    return null;
  }

  private static void validateMagnitude(BigDecimal units, BigDecimal amount) {
    if (units.abs().compareTo(MAX_REASONABLE_VALUE) > 0
        || amount.abs().compareTo(MAX_REASONABLE_VALUE) > 0) {
      throw new IllegalArgumentException(
          "R45 row value exceeds sanity limit: units=" + units + ", amount=" + amount);
    }
  }

  private static BigDecimal numberOrZero(Map<String, String> row, String keyword) {
    BigDecimal value = parseNumber(findValue(row, keyword));
    return value == null ? BigDecimal.ZERO : value;
  }

  private static String trimmedUpperCase(@Nullable String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeIsin(@Nullable String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s", "");
  }

  private static final class FlowAccumulator {
    private BigDecimal inflow = BigDecimal.ZERO;
    private BigDecimal outflow = BigDecimal.ZERO;

    private void add(R45TransactionType type, BigDecimal orderValue) {
      if (type.getDirection() == OUTFLOW) {
        outflow = outflow.add(orderValue);
      } else {
        inflow = inflow.add(orderValue);
      }
    }

    private R45Result toResult() {
      return new R45Result(inflow, outflow, inflow.subtract(outflow));
    }
  }
}
