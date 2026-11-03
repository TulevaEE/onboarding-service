package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  JdbcFundValueRepository.class,
  PastPerformanceConfiguration.class,
  PastPerformanceService.class,
  PastPerformanceServiceIT.DayAfterTheLastNav.class
})
class PastPerformanceServiceIT {

  private static final LocalDate AS_OF = LocalDate.of(2026, 10, 21);

  @Autowired private PastPerformanceService service;
  @Autowired private JdbcFundValueRepository fundValues;

  @BeforeEach
  void storeTheSeries() {
    fundValues.saveAll(SyntheticNavSeries.benchmark("MSCI_ACWI"));
    fundValues.saveAll(SyntheticNavSeries.bond("EURO_AGGREGATE_BOND"));
    fundValues.saveAll(
        SyntheticNavSeries.nav("EE3600109435", LocalDate.of(2017, 3, 28), "0.64000", 7));
    fundValues.saveAll(
        SyntheticNavSeries.nav("EE3600109443", LocalDate.of(2017, 3, 28), "0.64000", 13, 2019));
    fundValues.saveAll(
        List.of(aFundValue("EE0000003283", LocalDate.of(2026, 2, 1), new BigDecimal("1.00000"))));
    fundValues.saveAll(
        SyntheticNavSeries.nav("EE0000003283", LocalDate.of(2026, 2, 2), "1.00000", 99));
  }

  @Test
  void givesAPensionFundsYearsBesideItsBenchmarkHeldAtTheEquityCapUntilItWasLifted() {
    assertThat(service.pastPerformance(TUK75))
        .isEqualTo(
            new PastPerformance(
                TUK75,
                "EE3600109435",
                "MSCI_ACWI",
                new EquityCap(
                    "EURO_AGGREGATE_BOND", new BigDecimal("0.75"), LocalDate.of(2019, 9, 2)),
                AS_OF,
                true,
                List.of(
                    year(2017, "0.07788623", "0.21001151", true, false),
                    year(2018, "0.13557655", "0.18204845", false, false),
                    year(2019, "0.10857463", "0.18889511", false, false),
                    year(2020, "0.05761761", "-0.18833953", false, false),
                    year(2021, "0.10918554", "0.09291543", false, false),
                    year(2022, "0.02188372", "0.15006590", false, false),
                    year(2023, "0.04982793", "-0.08825630", false, false),
                    year(2024, "0.06795292", "0.10576947", false, false),
                    year(2025, "0.10215546", "-0.00230421", false, false),
                    year(2026, "0.12336801", "0.01282657", false, true))));
  }

  @Test
  void leavesOutTheYearAfterAGapRatherThanChainingAcrossItAndShowsNoBenchmarkWhereNoneIsHeld() {
    assertThat(service.pastPerformance(TUK00))
        .isEqualTo(
            new PastPerformance(
                TUK00,
                "EE3600109443",
                null,
                null,
                AS_OF,
                true,
                List.of(
                    year(2017, "0.06507857", true, false),
                    year(2018, "0.01125643", false, false),
                    year(2021, "0.07407765", false, false),
                    year(2022, "0.02005797", false, false),
                    year(2023, "0.06520227", false, false),
                    year(2024, "0.15722669", false, false),
                    year(2025, "0.05085999", false, false),
                    year(2026, "0.05700563", false, true))));
  }

  @Test
  void measuresTheSavingsFundsFirstYearAndItsBenchmarkFromTheIssueDayAndSaysItHasNoFullYearYet() {
    assertThat(service.pastPerformance(TKF100))
        .isEqualTo(
            new PastPerformance(
                TKF100,
                "EE0000003283",
                "MSCI_ACWI",
                null,
                AS_OF,
                false,
                List.of(year(2026, "0.01731000", "0.02964196", true, true))));
  }

  @Test
  void refusesAFundWithNoNavInTheSeries() {
    assertThatThrownBy(() -> service.pastPerformance(TUV100))
        .isInstanceOf(PastPerformanceRefusedException.class);
  }

  private static YearReturn year(
      int year, String fund, String benchmark, boolean partialYear, boolean yearToDate) {
    return new YearReturn(
        year, new BigDecimal(fund), new BigDecimal(benchmark), partialYear, yearToDate);
  }

  private static YearReturn year(int year, String fund, boolean partialYear, boolean yearToDate) {
    return new YearReturn(year, new BigDecimal(fund), null, partialYear, yearToDate);
  }

  @TestConfiguration
  static class DayAfterTheLastNav {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-22T09:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }
}
