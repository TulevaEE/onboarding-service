package ee.tuleva.onboarding.investment.epis.parser;

import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findDate;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findValue;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.parseNumber;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.epis.R17Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class R17ReportParser {

  private static final String HEADER_MARKER = "Väärtpaber";
  private static final DecimalConvention DECIMAL_CONVENTION = DecimalConvention.PERIOD_DECIMAL;
  private static final BigDecimal MAX_REASONABLE_UNITS = new BigDecimal("100000000");

  private final EpisCsvParser csvParser;

  public Map<String, R17Result> parse(String csv, LocalDate lockDate, LocalDate execDate) {
    EpisCsv parsed = csvParser.parse(csv, HEADER_MARKER);
    validateSeisugaDate(parsed.preHeaderLines(), lockDate, execDate);

    Map<String, UnitAccumulator> accumulators = new LinkedHashMap<>();
    for (Map<String, String> row : parsed.rows()) {
      String fundRaw = trimmed(findValue(row, "väärtpaber", "vaartpaber"));
      String toiming = lowerCase(findValue(row, "toiming"));
      if (fundRaw.isEmpty() || toiming.isEmpty()) {
        continue;
      }

      String pfType = lowerCase(findValue(row, "pf valitseja", "pfvalitseja"));
      BigDecimal units = requiredUnits(row, fundRaw, toiming).abs();
      if (units.signum() == 0) {
        continue;
      }
      if (units.compareTo(MAX_REASONABLE_UNITS) > 0) {
        throw new IllegalArgumentException("R17 row units exceed sanity limit: units=" + units);
      }

      Optional<TulevaFund> fund = FundResolver.resolve(fundRaw);
      if (fund.isEmpty()) {
        continue;
      }
      UnitAccumulator accumulator =
          accumulators.computeIfAbsent(fund.get().getCode(), code -> new UnitAccumulator());

      boolean isTagasivott = toiming.contains("tagasivõtt") || toiming.contains("tagasivott");
      boolean isValjalase = toiming.contains("väljalase") || toiming.contains("valjalase");
      boolean isPik = pfType.contains("pik");

      if (isTagasivott && isPik) {
        accumulator.pikUnits = accumulator.pikUnits.add(units);
      } else if (isValjalase) {
        accumulator.netUnits = accumulator.netUnits.add(units);
      } else if (isTagasivott) {
        accumulator.netUnits = accumulator.netUnits.subtract(units);
      }
    }

    Map<String, R17Result> results = new LinkedHashMap<>();
    accumulators.forEach(
        (fundCode, accumulator) ->
            results.put(fundCode, new R17Result(accumulator.pikUnits, accumulator.netUnits)));
    return results;
  }

  private static void validateSeisugaDate(
      List<String> preHeaderLines, LocalDate lockDate, LocalDate execDate) {
    LocalDate seisuga = findSeisugaDate(preHeaderLines);
    if (seisuga == null) {
      throw new IllegalArgumentException(
          "R17 Seisuga date marker missing or unparseable: preHeaderLineCount="
              + preHeaderLines.size());
    }
    if (seisuga.isBefore(lockDate) || seisuga.isAfter(execDate)) {
      throw new IllegalArgumentException(
          "R17 Seisuga date outside active cycle window: seisuga="
              + seisuga
              + ", lockDate="
              + lockDate
              + ", execDate="
              + execDate);
    }
  }

  @Nullable
  private static LocalDate findSeisugaDate(List<String> preHeaderLines) {
    for (int i = 0; i < preHeaderLines.size(); i++) {
      String line = preHeaderLines.get(i);
      if (line.toLowerCase(Locale.ROOT).contains("seisuga")) {
        LocalDate sameLineDate = findDate(line);
        if (sameLineDate != null) {
          return sameLineDate;
        }
        return i + 1 < preHeaderLines.size() ? findDate(preHeaderLines.get(i + 1)) : null;
      }
    }
    return null;
  }

  private static BigDecimal requiredUnits(Map<String, String> row, String fund, String toiming) {
    BigDecimal units =
        parseNumber(findValue(row, "osakud (teenustasuga)", "osakuid"), DECIMAL_CONVENTION);
    if (units == null) {
      throw new IllegalArgumentException(
          "R17 required units missing: fund=" + fund + ", toiming=" + toiming);
    }
    return units;
  }

  private static String trimmed(@Nullable String value) {
    return value == null ? "" : value.trim();
  }

  private static String lowerCase(@Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static final class UnitAccumulator {
    private BigDecimal pikUnits = BigDecimal.ZERO;
    private BigDecimal netUnits = BigDecimal.ZERO;
  }
}
