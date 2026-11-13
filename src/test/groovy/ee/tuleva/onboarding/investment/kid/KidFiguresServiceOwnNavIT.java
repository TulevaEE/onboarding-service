package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.FAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.WindowSource.FULL_LENGTH;
import static ee.tuleva.onboarding.investment.kid.WindowSource.STRESSED_VOLATILITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties;
import ee.tuleva.onboarding.investment.series.SeriesSegment;
import ee.tuleva.onboarding.investment.series.SplicedPriceSeries;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  JdbcFundValueRepository.class,
  SplicedPriceSeries.class,
  KidConfiguration.class,
  KidFiguresService.class,
  PerformanceScenarioCalculator.class,
  StressScenarioCalculator.class,
  ScenarioPresentation.class,
  CostsOverTimeCalculator.class,
  KidFiguresServiceOwnNavIT.IndexThenOwnNavFromMarch.class
})
class KidFiguresServiceOwnNavIT {

  private static final BigDecimal ONGOING_CHARGES = new BigDecimal("0.005");
  private static final List<SeriesSegment> INDEX_THEN_OWN_NAV =
      List.of(
          new SeriesSegment("MSCI_ACWI", null),
          new SeriesSegment(TKF100.getIsin(), LocalDate.of(2026, 3, 1)));

  @Autowired private KidFiguresService service;
  @Autowired private JdbcFundValueRepository fundValues;

  @Test
  void calculatesTheFiguresTheTeamsSqlGivesWhenTheFundsOwnNavSupplementsTheIndex() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    fundValues.saveAll(SyntheticIndexSeries.ownNavCloses(TKF100.getIsin()));

    var figures = service.figures(TKF100, ONGOING_CHARGES);

    assertThat(figures)
        .isEqualTo(
            new KidFigures(
                TKF100,
                INDEX_THEN_OWN_NAV,
                LocalDate.of(2016, 9, 30),
                LocalDate.of(2026, 9, 30),
                ONGOING_CHARGES,
                5,
                new BigDecimal("10000"),
                List.of(
                    new KidScenario(
                        1,
                        STRESS,
                        new BigDecimal("6640"),
                        new BigDecimal("-0.33564955"),
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
                        new BigDecimal("5300"),
                        new BigDecimal("-0.11932841"),
                        LocalDate.of(2022, 8, 1),
                        LocalDate.of(2022, 10, 26),
                        STRESSED_VOLATILITY,
                        null),
                    new KidScenario(
                        5,
                        UNFAVOURABLE,
                        new BigDecimal("9280"),
                        new BigDecimal("-0.01475483"),
                        LocalDate.of(2019, 8, 31),
                        LocalDate.of(2024, 8, 31),
                        FULL_LENGTH,
                        60),
                    new KidScenario(
                        5,
                        MODERATE,
                        new BigDecimal("11650"),
                        new BigDecimal("0.03095777"),
                        LocalDate.of(2021, 8, 31),
                        LocalDate.of(2026, 8, 31),
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
                        new BigDecimal("276"),
                        new BigDecimal("0.005"),
                        new BigDecimal("0.036"),
                        new BigDecimal("0.031")))));
  }

  @Test
  void refusesWhenTheOwnNavHasNoCloseOnTheIndexsLastDayBeforeTheSplice() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    fundValues.saveAll(
        SyntheticIndexSeries.ownNavCloses(TKF100.getIsin()).stream()
            .filter(close -> !close.date().equals(LocalDate.of(2026, 2, 27)))
            .toList());

    assertThatThrownBy(() -> service.figures(TKF100, ONGOING_CHARGES))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesWhenTheOwnNavHasNoPricesAtAll() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));

    assertThatThrownBy(() -> service.figures(TKF100, ONGOING_CHARGES))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @TestConfiguration
  static class IndexThenOwnNavFromMarch {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-22T09:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }

    @Bean
    RiskIndicatorProperties riskIndicatorProperties() {
      return new RiskIndicatorProperties(Map.of(TKF100, INDEX_THEN_OWN_NAV));
    }
  }
}
