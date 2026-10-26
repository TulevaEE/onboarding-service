package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.FAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.WindowSource.FULL_LENGTH;
import static ee.tuleva.onboarding.investment.kid.WindowSource.SHORTER;
import static ee.tuleva.onboarding.investment.kid.WindowSource.STRESSED_VOLATILITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  JdbcFundValueRepository.class,
  KidConfiguration.class,
  KidFiguresService.class,
  PerformanceScenarioCalculator.class,
  StressScenarioCalculator.class,
  ScenarioPresentation.class,
  CostsOverTimeCalculator.class,
  KidFiguresServiceIT.DayAfterTheSeptemberPeriodEnds.class
})
class KidFiguresServiceIT {

  private static final BigDecimal ONGOING_CHARGES = new BigDecimal("0.005");

  @Autowired private KidFiguresService service;
  @Autowired private JdbcFundValueRepository fundValues;

  @Test
  void calculatesTheTeamsSqlFiguresExceptTheStressWhichMeasuresOnlyTheLastFiveYears() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));

    var figures = service.figures(TKF100, ONGOING_CHARGES);

    assertThat(figures)
        .isEqualTo(
            new KidFigures(
                TKF100,
                "MSCI_ACWI",
                LocalDate.of(2016, 9, 30),
                LocalDate.of(2026, 9, 30),
                ONGOING_CHARGES,
                5,
                new BigDecimal("10000"),
                List.of(
                    new KidScenario(
                        1,
                        STRESS,
                        new BigDecimal("6620"),
                        new BigDecimal("-0.33830406"),
                        LocalDate.of(2022, 4, 22),
                        LocalDate.of(2022, 5, 20),
                        STRESSED_VOLATILITY,
                        null),
                    new KidScenario(
                        1,
                        UNFAVOURABLE,
                        new BigDecimal("6940"),
                        new BigDecimal("-0.30616435"),
                        LocalDate.of(2019, 8, 31),
                        LocalDate.of(2020, 8, 31),
                        FULL_LENGTH,
                        12),
                    new KidScenario(
                        1,
                        MODERATE,
                        new BigDecimal("11000"),
                        new BigDecimal("0.10024062"),
                        LocalDate.of(2023, 12, 31),
                        LocalDate.of(2024, 12, 31),
                        FULL_LENGTH,
                        12),
                    new KidScenario(
                        1,
                        FAVOURABLE,
                        new BigDecimal("14540"),
                        new BigDecimal("0.45396934"),
                        LocalDate.of(2017, 4, 30),
                        LocalDate.of(2018, 4, 30),
                        FULL_LENGTH,
                        12),
                    new KidScenario(
                        5,
                        STRESS,
                        new BigDecimal("5290"),
                        new BigDecimal("-0.11955089"),
                        LocalDate.of(2022, 8, 1),
                        LocalDate.of(2022, 10, 26),
                        STRESSED_VOLATILITY,
                        null),
                    new KidScenario(
                        5,
                        UNFAVOURABLE,
                        new BigDecimal("8620"),
                        new BigDecimal("-0.02929577"),
                        LocalDate.of(2025, 9, 30),
                        LocalDate.of(2026, 9, 30),
                        SHORTER,
                        12),
                    new KidScenario(
                        5,
                        MODERATE,
                        new BigDecimal("11480"),
                        new BigDecimal("0.02807509"),
                        LocalDate.of(2019, 1, 31),
                        LocalDate.of(2024, 1, 31),
                        FULL_LENGTH,
                        60),
                    new KidScenario(
                        5,
                        FAVOURABLE,
                        new BigDecimal("19250"),
                        new BigDecimal("0.13990625"),
                        LocalDate.of(2017, 9, 30),
                        LocalDate.of(2022, 9, 30),
                        FULL_LENGTH,
                        60)),
                List.of(
                    new KidCost(
                        1,
                        new BigDecimal("50"),
                        new BigDecimal("0.005"),
                        new BigDecimal("0.005"),
                        new BigDecimal("0.000")),
                    new KidCost(
                        5,
                        new BigDecimal("273"),
                        new BigDecimal("0.005"),
                        new BigDecimal("0.033"),
                        new BigDecimal("0.028")))));
  }

  @Test
  void refusesWhenTheSeriesHasNoPricesAtAll() {
    assertThatThrownBy(() -> service.figures(TKF100, ONGOING_CHARGES))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesASeriesWithAPriceThatIsNotPositiveRatherThanShowingAQuietlyWrongStress() {
    fundValues.saveAll(
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .map(
                close ->
                    close.date().equals(LocalDate.of(2021, 6, 15))
                        ? aFundValue("MSCI_ACWI", close.date(), BigDecimal.ZERO)
                        : close)
            .toList());

    assertThatThrownBy(() -> service.figures(TKF100, ONGOING_CHARGES))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesAFeedThatStoppedRatherThanGivingAnOlderPeriodsFigures() {
    fundValues.saveAll(
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .filter(close -> !close.date().isAfter(LocalDate.of(2026, 3, 13)))
            .toList());

    assertThatThrownBy(() -> service.figures(TKF100, ONGOING_CHARGES))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesAFundWithNoKidSeriesConfigured() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));

    assertThatThrownBy(() -> service.figures(TUK75, ONGOING_CHARGES))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @TestConfiguration
  static class DayAfterTheSeptemberPeriodEnds {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-22T09:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }
}
