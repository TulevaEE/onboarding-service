package ee.tuleva.onboarding.investment.epis.parser;

import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findValue;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.parseNumber;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.epis.R21Result;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class R21ReportParser {

  private static final String HEADER_MARKER = "Väärtpaber";
  private static final DecimalConvention DECIMAL_CONVENTION = DecimalConvention.COMMA_DECIMAL;
  private static final BigDecimal MAX_REASONABLE_UNITS = new BigDecimal("100000000");
  private static final Pattern MAKSETE_KUU = Pattern.compile("[Mm]aksete\\s*kuu[:\\s]*(\\d{6})");
  private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

  private final EpisCsvParser csvParser;

  public Map<String, R21Result> parse(String csv, YearMonth expectedExecutionMonth) {
    EpisCsv parsed = csvParser.parse(csv, HEADER_MARKER);
    validateMakseteKuu(parsed.preHeaderLines(), expectedExecutionMonth);

    Map<String, BigDecimal> unitsByFund = new LinkedHashMap<>();
    for (Map<String, String> row : parsed.rows()) {
      String fundRaw = findValue(row, "väärtpaber", "vaartpaber");
      BigDecimal parsedUnits = parseNumber(findValue(row, "osakud"), DECIMAL_CONVENTION);
      BigDecimal units = parsedUnits == null ? BigDecimal.ZERO : parsedUnits.abs();

      if (fundRaw == null || fundRaw.isBlank() || units.signum() == 0) {
        continue;
      }
      if (units.compareTo(MAX_REASONABLE_UNITS) > 0) {
        throw new IllegalArgumentException("R21 row units exceed sanity limit: units=" + units);
      }

      Optional<TulevaFund> fund = FundResolver.resolve(fundRaw);
      if (fund.isEmpty()) {
        continue;
      }
      unitsByFund.merge(fund.get().getCode(), units, BigDecimal::add);
    }

    Map<String, R21Result> results = new LinkedHashMap<>();
    unitsByFund.forEach((fundCode, units) -> results.put(fundCode, new R21Result(units)));
    return results;
  }

  private static void validateMakseteKuu(
      List<String> preHeaderLines, YearMonth expectedExecutionMonth) {
    String makseteKuu = findMakseteKuu(preHeaderLines);
    if (makseteKuu == null) {
      throw new IllegalArgumentException(
          "R21 Maksete kuu marker missing: preHeaderLineCount=" + preHeaderLines.size());
    }
    String expected = expectedExecutionMonth.format(YEAR_MONTH);
    if (!makseteKuu.equals(expected)) {
      throw new IllegalArgumentException(
          "R21 Maksete kuu does not match expected execution month: makseteKuu="
              + makseteKuu
              + ", expected="
              + expected);
    }
  }

  @Nullable
  private static String findMakseteKuu(List<String> preHeaderLines) {
    for (String line : preHeaderLines) {
      Matcher matcher = MAKSETE_KUU.matcher(line);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    return null;
  }
}
