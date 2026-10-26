package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PastPerformanceServiceTest {

  private final FundValueQueries fundValueQueries = mock(FundValueQueries.class);
  private final PastPerformanceService service =
      new PastPerformanceService(
          new PastPerformanceProperties(Map.of(TUK75, "MSCI_ACWI")),
          fundValueQueries,
          Clock.fixed(Instant.parse("2026-02-10T09:00:00Z"), ZoneId.of("Europe/Tallinn")));

  @Test
  void leavesOutABenchmarkYearThatTheBenchmarkHistoryOnlyPartlyCovers() {
    var fund =
        List.of(
            aFundValue("EE3600109435", LocalDate.of(2023, 12, 29), 1.0),
            aFundValue("EE3600109435", LocalDate.of(2024, 12, 31), 1.1),
            aFundValue("EE3600109435", LocalDate.of(2025, 12, 31), 1.21));
    var benchmark =
        List.of(
            aFundValue("MSCI_ACWI", LocalDate.of(2024, 6, 28), 100.0),
            aFundValue("MSCI_ACWI", LocalDate.of(2024, 12, 31), 120.0),
            aFundValue("MSCI_ACWI", LocalDate.of(2025, 12, 31), 132.0));
    given(fundValueQueries.findLastValueForFund("EE3600109435"))
        .willReturn(Optional.of(fund.getLast()));
    given(fundValueQueries.findLastValueForFund("MSCI_ACWI"))
        .willReturn(Optional.of(benchmark.getLast()));
    given(fundValueQueries.findEarliestDateForKey("EE3600109435"))
        .willReturn(Optional.of(LocalDate.of(2017, 3, 28)));
    given(fundValueQueries.findEarliestDateForKey("MSCI_ACWI"))
        .willReturn(Optional.of(LocalDate.of(2024, 6, 28)));
    given(fundValueQueries.findValuesBetweenDates(any(), any(), any()))
        .willAnswer(invocation -> "MSCI_ACWI".equals(invocation.getArgument(0)) ? benchmark : fund);

    var years = service.pastPerformance(TUK75).years();

    assertThat(years)
        .containsExactly(
            new YearReturn(2024, new BigDecimal("0.10000000"), null, false, false),
            new YearReturn(
                2025, new BigDecimal("0.10000000"), new BigDecimal("0.10000000"), false, true));
  }

  @Test
  void aYearTheBenchmarkHasNotClosedYetStaysTheYearToDateInJanuaryAfterTenFullYears() {
    var januaryService =
        new PastPerformanceService(
            new PastPerformanceProperties(Map.of(TUK75, "MSCI_ACWI")),
            fundValueQueries,
            Clock.fixed(Instant.parse("2028-01-04T09:00:00Z"), ZoneId.of("Europe/Tallinn")));
    var fund =
        Stream.of(
                Stream.of(aFundValue("EE3600109435", LocalDate.of(2016, 3, 28), 1.0)),
                IntStream.rangeClosed(2016, 2026)
                    .mapToObj(
                        year ->
                            aFundValue(
                                "EE3600109435",
                                LocalDate.of(year, 12, 31),
                                1.05 * Math.pow(1.1, year - 2016))),
                Stream.of(
                    aFundValue(
                        "EE3600109435",
                        LocalDate.of(2027, 12, 29),
                        1.05 * Math.pow(1.1, 10) * 1.08),
                    aFundValue(
                        "EE3600109435", LocalDate.of(2027, 12, 31), 1.05 * Math.pow(1.1, 10) * 1.1),
                    aFundValue(
                        "EE3600109435", LocalDate.of(2028, 1, 3), 1.05 * Math.pow(1.1, 10) * 1.12)))
            .flatMap(values -> values)
            .toList();
    var benchmark =
        Stream.concat(
                IntStream.rangeClosed(2015, 2026)
                    .mapToObj(
                        year ->
                            aFundValue(
                                "MSCI_ACWI",
                                LocalDate.of(year, 12, 31),
                                100 * Math.pow(1.05, year - 2015))),
                Stream.of(
                    aFundValue(
                        "MSCI_ACWI", LocalDate.of(2027, 12, 29), 100 * Math.pow(1.05, 11) * 1.03)))
            .toList();
    givenTheSeries(fund, LocalDate.of(2016, 3, 28), benchmark, LocalDate.of(2015, 12, 31));

    assertThat(januaryService.pastPerformance(TUK75))
        .isEqualTo(
            new PastPerformance(
                TUK75,
                "EE3600109435",
                "MSCI_ACWI",
                LocalDate.of(2027, 12, 29),
                true,
                Stream.concat(
                        IntStream.rangeClosed(2017, 2026)
                            .mapToObj(
                                year ->
                                    new YearReturn(
                                        year,
                                        new BigDecimal("0.10000000"),
                                        new BigDecimal("0.05000000"),
                                        false,
                                        false)),
                        Stream.of(
                            new YearReturn(
                                2027,
                                new BigDecimal("0.08000000"),
                                new BigDecimal("0.03000000"),
                                false,
                                true)))
                    .toList()));
  }

  private void givenTheSeries(
      List<FundValue> fund,
      LocalDate fundStart,
      List<FundValue> benchmark,
      LocalDate benchmarkStart) {
    given(fundValueQueries.findLastValueForFund("EE3600109435"))
        .willReturn(Optional.of(fund.getLast()));
    given(fundValueQueries.findLastValueForFund("MSCI_ACWI"))
        .willReturn(Optional.of(benchmark.getLast()));
    given(fundValueQueries.findEarliestDateForKey("EE3600109435"))
        .willReturn(Optional.of(fundStart));
    given(fundValueQueries.findEarliestDateForKey("MSCI_ACWI"))
        .willReturn(Optional.of(benchmarkStart));
    given(fundValueQueries.findValuesBetweenDates(any(), any(), any()))
        .willAnswer(
            invocation -> {
              var series = "MSCI_ACWI".equals(invocation.getArgument(0)) ? benchmark : fund;
              LocalDate from = invocation.getArgument(1);
              LocalDate to = invocation.getArgument(2);
              return series.stream()
                  .filter(value -> !value.date().isBefore(from) && !value.date().isAfter(to))
                  .toList();
            });
  }
}
