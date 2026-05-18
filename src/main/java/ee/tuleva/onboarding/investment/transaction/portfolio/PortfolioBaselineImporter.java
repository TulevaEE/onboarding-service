package ee.tuleva.onboarding.investment.transaction.portfolio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioBaselineImporter {

  private static final String COL_FUND_ISIN = "fund_isin";
  private static final String COL_INSTRUMENT_ISIN = "instrument_isin";
  private static final String COL_QUANTITY = "quantity";
  private static final String COL_AVG_UNIT_COST = "avg_unit_cost";
  private static final String COL_BASELINE_DATE = "baseline_date";

  private final PortfolioBaselineRepository baselineRepository;

  @Transactional
  public List<PortfolioBaseline> importCsv(InputStream csv, String loadedBy) {
    Map<String, PortfolioBaseline> byFund = parseRows(csv, loadedBy);
    List<PortfolioBaseline> saved = new ArrayList<>();
    for (PortfolioBaseline candidate : byFund.values()) {
      baselineRepository
          .findByFundIsin(candidate.getFundIsin())
          .ifPresent(baselineRepository::delete);
      baselineRepository.flush();
      saved.add(baselineRepository.save(candidate));
    }
    log.info("Imported portfolio baselines: count={}, loadedBy={}", saved.size(), loadedBy);
    return saved;
  }

  private Map<String, PortfolioBaseline> parseRows(InputStream csv, String loadedBy) {
    Map<String, PortfolioBaseline> byFund = new LinkedHashMap<>();
    try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8));
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(reader)) {

      for (CSVRecord record : parser) {
        String fundIsin = required(record, COL_FUND_ISIN);
        String instrumentIsin = required(record, COL_INSTRUMENT_ISIN);
        BigDecimal quantity = new BigDecimal(required(record, COL_QUANTITY));
        BigDecimal avgUnitCost = new BigDecimal(required(record, COL_AVG_UNIT_COST));
        LocalDate baselineDate = LocalDate.parse(required(record, COL_BASELINE_DATE));

        PortfolioBaseline baseline =
            byFund.computeIfAbsent(
                fundIsin,
                isin ->
                    PortfolioBaseline.builder()
                        .fundIsin(isin)
                        .baselineDate(baselineDate)
                        .loadedBy(loadedBy)
                        .build());

        if (!baseline.getBaselineDate().equals(baselineDate)) {
          throw new IllegalArgumentException(
              "Mixed baseline_date for fund_isin="
                  + fundIsin
                  + ": expected="
                  + baseline.getBaselineDate()
                  + ", got="
                  + baselineDate);
        }
        baseline.addEntry(
            PortfolioBaselineEntry.builder()
                .instrumentIsin(instrumentIsin)
                .quantity(quantity)
                .avgUnitCost(avgUnitCost)
                .build());
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to parse baseline CSV", e);
    }
    return byFund;
  }

  private String required(CSVRecord record, String column) {
    if (!record.isMapped(column)) {
      throw new IllegalArgumentException("Missing required CSV column: " + column);
    }
    String value = record.get(column);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Missing required value for column: column="
              + column
              + ", recordNumber="
              + record.getRecordNumber());
    }
    return value;
  }
}
