package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.FAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationReadConfiguration;
import ee.tuleva.onboarding.investment.kid.KidPublicationRequest.MonthToPublish;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({
  JdbcFundValueRepository.class,
  IndexOnlyKidSeries.class,
  KidConfiguration.class,
  KidFiguresService.class,
  PerformanceScenarioCalculator.class,
  StressScenarioCalculator.class,
  ScenarioPresentation.class,
  CostsOverTimeCalculator.class,
  KidCalculationRepository.class,
  KidCalculationService.class,
  KidPublicationRepository.class,
  KidPublicationService.class,
  KidCalculationReader.class,
  KidPrintedFiguresRepository.class,
  OcfPublicationReadConfiguration.class,
  KidCalculationReaderIT.DayAfterTheSeptemberPeriodEnds.class
})
class KidCalculationReaderIT {

  private static final YearMonth JUNE = YearMonth.of(2026, 6);
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");
  private static final String PUBLISHER = "portfolio-manager";
  private static final List<PrintedScenario> SEPTEMBER_AT_HALF_A_PERCENT =
      List.of(
          printed(1, STRESS, "6620", "-0.3383"),
          printed(1, UNFAVOURABLE, "6940", "-0.3062"),
          printed(1, MODERATE, "11000", "0.1002"),
          printed(1, FAVOURABLE, "14540", "0.4540"),
          printed(5, STRESS, "5290", "-0.1196"),
          printed(5, UNFAVOURABLE, "8620", "-0.0293"),
          printed(5, MODERATE, "11480", "0.0281"),
          printed(5, FAVOURABLE, "19250", "0.1399"));
  private static final List<PrintedCost> SEPTEMBER_COSTS_AT_HALF_A_PERCENT =
      List.of(
          new PrintedCost(
              1,
              new BigDecimal("50"),
              new BigDecimal("0.0050"),
              new BigDecimal("0.0050"),
              new BigDecimal("0.0000")),
          new PrintedCost(
              5,
              new BigDecimal("273"),
              new BigDecimal("0.0052"),
              new BigDecimal("0.0332"),
              new BigDecimal("0.0281")));

  @Autowired private KidCalculationReader reader;
  @Autowired private KidCalculationService calculations;
  @Autowired private KidPublicationService publications;
  @Autowired private JdbcFundValueRepository fundValues;
  @Autowired private JdbcClient jdbcClient;

  private long kid;

  @BeforeEach
  void pricesAndAnOcfInForceFromAugust() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    kid = KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 8, 3), "0.00500000");
  }

  @Test
  void listsEachMonthsLatestVersionOldestFirstWithItsPrintedFiguresAndItsPublication() {
    var june = calculations.calculateAndStore(TKF100, JUNE);
    var august = calculations.calculateAndStore(TKF100, AUGUST);
    var published = calculations.calculateAndStore(TKF100, SEPTEMBER);
    publications.publish(
        TKF100,
        new KidPublicationRequest(
            PUBLISHER, List.of(new MonthToPublish(SEPTEMBER, published.id()))));
    var newer = calculations.calculateAndStore(TKF100, SEPTEMBER);

    var read = reader.read(TKF100, Optional.empty(), Optional.empty()).orElseThrow();

    assertThat(read)
        .extracting(KidMonthCalculation::month)
        .containsExactly(JUNE, AUGUST, SEPTEMBER);
    assertThat(read.getFirst())
        .isEqualTo(
            new KidMonthCalculation(
                JUNE, june.id(), 1, NOW, june.refusalReasons(), null, List.of(), List.of(), null));
    assertThat(read.get(1).calculationId()).isEqualTo(august.id());
    assertThat(read.get(1).published()).isNull();
    assertThat(read.getLast())
        .isEqualTo(
            new KidMonthCalculation(
                SEPTEMBER,
                newer.id(),
                2,
                NOW,
                List.of(),
                new OcfBasis(kid, LocalDate.of(2026, 8, 3), new BigDecimal("0.00500000"), null),
                SEPTEMBER_AT_HALF_A_PERCENT,
                SEPTEMBER_COSTS_AT_HALF_A_PERCENT,
                new KidPublication(TKF100, SEPTEMBER, published.id(), PUBLISHER, NOW)));
  }

  @Test
  void readsOnlyTheMonthsAskedFor() {
    calculations.calculateAndStore(TKF100, AUGUST);
    calculations.calculateAndStore(TKF100, SEPTEMBER);

    assertThat(reader.read(TKF100, Optional.of(SEPTEMBER), Optional.of(SEPTEMBER)).orElseThrow())
        .extracting(KidMonthCalculation::month)
        .containsExactly(SEPTEMBER);
  }

  @Test
  void readsNothingForAMonthWithoutACalculation() {
    assertThat(reader.read(TKF100, Optional.empty(), Optional.empty())).contains(List.of());
  }

  @Test
  void readsNoFundWithoutAKid() {
    assertThat(reader.read(TUK75, Optional.empty(), Optional.empty())).isEmpty();
  }

  @Test
  void refusesARangeThatEndsBeforeItStarts() {
    assertThatThrownBy(() -> reader.read(TKF100, Optional.of(SEPTEMBER), Optional.of(AUGUST)))
        .isInstanceOf(InvalidKidRequestException.class);
  }

  private static PrintedScenario printed(
      int horizonYears, ScenarioKind kind, String amount, String averageReturn) {
    return new PrintedScenario(
        horizonYears, kind, new BigDecimal(amount), new BigDecimal(averageReturn));
  }

  @TestConfiguration
  static class DayAfterTheSeptemberPeriodEnds {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneId.of("Europe/Tallinn"));
    }
  }
}
