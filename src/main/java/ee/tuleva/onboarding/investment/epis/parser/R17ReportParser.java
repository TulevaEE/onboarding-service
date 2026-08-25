package ee.tuleva.onboarding.investment.epis.parser;

import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findValue;
import static ee.tuleva.onboarding.investment.epis.parser.EpisDates.findDate;
import static ee.tuleva.onboarding.investment.epis.parser.EpisNumbers.parseNumber;
import static java.math.BigDecimal.ZERO;
import static java.util.Objects.requireNonNullElse;

import ee.tuleva.onboarding.investment.epis.R17Result;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
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
  private static final BigDecimal MIN_AMOUNT_TOLERANCE_EUR = new BigDecimal("0.02");
  private static final BigDecimal HALF_PRICE_STEP = new BigDecimal("0.000005");

  private final EpisCsvParser csvParser;

  public Map<String, R17Result> parse(String csv, LocalDate lockDate, LocalDate execDate) {
    EpisCsv parsed = csvParser.parse(csv, HEADER_MARKER);
    validateSeisugaDate(parsed.preHeaderLines(), lockDate, execDate);

    Map<String, UnitAccumulator> accumulators = new LinkedHashMap<>();
    for (Map<String, String> row : parsed.rows()) {
      RowProcessor.processRow(row, accumulators);
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

  private static void validateOurRow(
      Map<String, String> row, String fund, String toiming, BigDecimal units) {
    if (units.compareTo(MAX_REASONABLE_UNITS) > 0) {
      throw new IllegalArgumentException(
          "R17 row units exceed sanity limit: fund=" + fund + ", units=" + units);
    }
    validateUnitsAgainstReportedAmount(row, fund, toiming, units);
  }

  private static void validateUnitsAgainstReportedAmount(
      Map<String, String> row, String fund, String toiming, BigDecimal units) {
    String priceCell = findValue(row, "hind");
    String amountCell = findValue(row, "summa");
    if (priceCell == null || amountCell == null) {
      return;
    }
    BigDecimal reportedAmount = parseNumber(amountCell, DECIMAL_CONVENTION);
    if (reportedAmount == null || isNotApplicable(reportedAmount)) {
      return;
    }
    BigDecimal price = requiredPrice(priceCell, fund, toiming, units);
    BigDecimal expectedAmount = units.multiply(price);
    if (expectedAmount.subtract(reportedAmount.abs()).abs().compareTo(amountTolerance(units)) > 0) {
      throw new IllegalArgumentException(
          "R17 units do not match the reported amount: fund="
              + fund
              + ", toiming="
              + toiming
              + ", units="
              + units
              + ", price="
              + price
              + ", expectedAmount="
              + expectedAmount
              + ", reportedAmount="
              + reportedAmount);
    }
  }

  private static boolean isNotApplicable(BigDecimal reportedAmount) {
    return reportedAmount.signum() == 0;
  }

  private static BigDecimal requiredPrice(
      String priceCell, String fund, String toiming, BigDecimal units) {
    BigDecimal price = parseNumber(priceCell, DECIMAL_CONVENTION);
    if (price == null || price.signum() <= 0) {
      throw new IllegalArgumentException(
          "R17 row has units and a reported amount but no usable price: fund="
              + fund
              + ", toiming="
              + toiming
              + ", units="
              + units
              + ", price="
              + priceCell);
    }
    return price;
  }

  private static BigDecimal amountTolerance(BigDecimal units) {
    return units.multiply(HALF_PRICE_STEP).max(MIN_AMOUNT_TOLERANCE_EUR);
  }

  private static BigDecimal requiredUnits(Map<String, String> row, String fund, String toiming) {
    BigDecimal feeBearingUnits =
        parseNumber(findValue(row, "osakud (teenustasuga)", "osakuid"), DECIMAL_CONVENTION);
    BigDecimal feeFreeUnits =
        parseNumber(findValue(row, "osakud (teenustasuta)"), DECIMAL_CONVENTION);
    if (feeBearingUnits == null && feeFreeUnits == null) {
      throw new IllegalArgumentException(
          "R17 required units missing: fund=" + fund + ", toiming=" + toiming);
    }
    return requireNonNullElse(feeBearingUnits, ZERO).add(requireNonNullElse(feeFreeUnits, ZERO));
  }

  private static String trimmed(@Nullable String value) {
    return value == null ? "" : value.trim();
  }

  private static String lowerCase(@Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static final class UnitAccumulator {
    private BigDecimal pikUnits = ZERO;
    private BigDecimal netUnits = ZERO;
  }

  private static final class RowProcessor {

    private static void processRow(
        Map<String, String> row, Map<String, UnitAccumulator> accumulators) {
      String fundRaw = trimmed(findValue(row, "väärtpaber", "vaartpaber"));
      String toiming = lowerCase(findValue(row, "toiming"));
      if (fundRaw.isEmpty() || toiming.isEmpty()) {
        return;
      }

      // "PF valitseja/PIK" is the real column name, verified against a genuine EPIS R17 export
      // (Seisuga 23.08.2026). It must come first: the report ALSO carries "Summa (PF valitseja)",
      // and both contain "pf valitseja", so a contains-match picks whichever column comes first.
      // That happens to be the right one today only because the amount column sits to its right —
      // a reordered export would silently classify PIK redemptions as switching flows, and the
      // units-vs-amount cross-check cannot catch it because the row's units are unchanged.
      String pfType = lowerCase(findValue(row, "pf valitseja/pik", "pf valitseja", "pfvalitseja"));
      BigDecimal units = requiredUnits(row, fundRaw, toiming).abs();
      if (units.signum() == 0) {
        return;
      }
      Optional<TulevaFund> fund = FundResolver.resolve(fundRaw);
      if (fund.isEmpty()) {
        return;
      }
      validateOurRow(row, fundRaw, toiming, units);

      UnitAccumulator accumulator =
          accumulators.computeIfAbsent(fund.get().getCode(), code -> new UnitAccumulator());
      applyToiming(accumulator, toiming, pfType, units);
    }

    private static void applyToiming(
        UnitAccumulator accumulator, String toiming, String pfType, BigDecimal units) {
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
  }
}
