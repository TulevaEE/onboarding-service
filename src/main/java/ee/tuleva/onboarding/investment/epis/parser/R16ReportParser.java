package ee.tuleva.onboarding.investment.epis.parser;

import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findValue;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.parseNumber;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class R16ReportParser {

  private static final String HEADER_MARKER = "Väärtpaber";
  private static final BigDecimal MAX_REASONABLE_UNITS = new BigDecimal("100000000");
  private static final Pattern KUU = Pattern.compile("[Kk]uu[:\\s]*(\\d{4})\\s*(\\d{2})");

  private final EpisCsvParser csvParser;

  public Map<String, R16ParsedFlow> parse(String csv) {
    EpisCsv parsed = csvParser.parse(csv, HEADER_MARKER);
    YearMonth paymentMonth = findPaymentMonth(parsed.preHeaderLines());

    Map<String, UnitAccumulator> accumulators = new LinkedHashMap<>();
    for (Map<String, String> row : parsed.rows()) {
      Optional<TulevaFund> fund = FundResolver.resolve(findValue(row, "väärtpaber", "vaartpaber"));
      if (fund.isEmpty()) {
        continue;
      }

      BigDecimal fondimaksedUnits = unitsOrZero(row, "fondimaksed osakud");
      BigDecimal uhekordsedUnits =
          unitsOrZero(row, "ühekordsed maksed osakud", "uhekordsed maksed osakud");
      validateMagnitude(fondimaksedUnits, uhekordsedUnits);

      UnitAccumulator accumulator =
          accumulators.computeIfAbsent(
              fund.get().getCode(), code -> new UnitAccumulator(fund.get()));
      accumulator.fondimaksedUnits = accumulator.fondimaksedUnits.add(fondimaksedUnits);
      accumulator.uhekordsedUnits = accumulator.uhekordsedUnits.add(uhekordsedUnits);
    }

    Map<String, R16ParsedFlow> results = new LinkedHashMap<>();
    accumulators.forEach(
        (fundCode, accumulator) ->
            results.put(
                fundCode,
                new R16ParsedFlow(
                    accumulator.fund,
                    accumulator.fondimaksedUnits,
                    accumulator.uhekordsedUnits,
                    paymentMonth)));
    return results;
  }

  private static YearMonth findPaymentMonth(List<String> preHeaderLines) {
    for (String line : preHeaderLines) {
      Matcher matcher = KUU.matcher(line);
      if (matcher.find()) {
        return YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
      }
    }
    throw new IllegalArgumentException("R16 Kuu header row not found");
  }

  private static void validateMagnitude(BigDecimal fondimaksedUnits, BigDecimal uhekordsedUnits) {
    if (fondimaksedUnits.compareTo(MAX_REASONABLE_UNITS) > 0
        || uhekordsedUnits.compareTo(MAX_REASONABLE_UNITS) > 0) {
      throw new IllegalArgumentException(
          "R16 row units exceed sanity limit: fondimaksedUnits="
              + fondimaksedUnits
              + ", uhekordsedUnits="
              + uhekordsedUnits);
    }
  }

  private static BigDecimal unitsOrZero(Map<String, String> row, String... keywords) {
    BigDecimal units = parseNumber(findValue(row, keywords));
    return units == null ? BigDecimal.ZERO : units.abs();
  }

  private static final class UnitAccumulator {
    private final TulevaFund fund;
    private BigDecimal fondimaksedUnits = BigDecimal.ZERO;
    private BigDecimal uhekordsedUnits = BigDecimal.ZERO;

    private UnitAccumulator(TulevaFund fund) {
      this.fund = fund;
    }
  }
}
