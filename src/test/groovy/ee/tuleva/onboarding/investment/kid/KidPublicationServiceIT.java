package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.FAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willReturn;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
  OcfPublicationReadConfiguration.class,
  KidPublicationServiceIT.DayAfterTheSeptemberPeriodEnds.class
})
class KidPublicationServiceIT {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");
  private static final String PUBLISHER = "portfolio-manager";
  private static final List<PublishedScenario> SEPTEMBER_AT_HALF_A_PERCENT =
      List.of(
          published(SEPTEMBER, 1, STRESS, "6620", "-0.3383"),
          published(SEPTEMBER, 1, UNFAVOURABLE, "6940", "-0.3062"),
          published(SEPTEMBER, 1, MODERATE, "11000", "0.1002"),
          published(SEPTEMBER, 1, FAVOURABLE, "14540", "0.4540"),
          published(SEPTEMBER, 5, STRESS, "5290", "-0.1196"),
          published(SEPTEMBER, 5, UNFAVOURABLE, "8620", "-0.0293"),
          published(SEPTEMBER, 5, MODERATE, "11480", "0.0281"),
          published(SEPTEMBER, 5, FAVOURABLE, "19250", "0.1399"));

  @Autowired private KidCalculationService calculations;
  @Autowired private KidPublicationService publications;
  @Autowired private JdbcFundValueRepository fundValues;
  @Autowired private JdbcClient jdbcClient;
  @MockitoSpyBean private KidCalculationRepository calculationRepository;

  @BeforeEach
  void pricesAndAnOcfInForceFromAugust() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 8, 3), "0.00500000");
  }

  @Test
  void publishesTheNamedCalculationOfEachMonthAndServesThemNewestFirst() {
    var august = calculated(AUGUST);
    var september = calculated(SEPTEMBER);

    var published =
        publications.publish(
            TKF100,
            new KidPublicationRequest(
                PUBLISHER,
                List.of(
                    new MonthToPublish(AUGUST, august.id()),
                    new MonthToPublish(SEPTEMBER, september.id()))));

    assertThat(published)
        .containsExactly(
            new KidPublication(TKF100, AUGUST, august.id(), PUBLISHER, NOW),
            new KidPublication(TKF100, SEPTEMBER, september.id(), PUBLISHER, NOW));
    var history = publications.publishedScenarioHistory(TKF100.getIsin()).orElseThrow();
    assertThat(history).hasSize(16);
    assertThat(history.subList(0, 8)).isEqualTo(SEPTEMBER_AT_HALF_A_PERCENT);
    assertThat(history.subList(8, 16)).extracting(PublishedScenario::month).containsOnly(AUGUST);
  }

  @Test
  void servesNoMonthUntilItIsPublished() {
    calculated(SEPTEMBER);

    assertThat(publications.publishedScenarioHistory(TKF100.getIsin())).contains(List.of());
  }

  @Test
  void aPublishedMonthKeepsItsCalculationWhenTheMonthIsRecalculated() {
    var september = calculated(SEPTEMBER);
    publish(SEPTEMBER, september.id());
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 25), "0.04000000");

    var recalculated = calculations.calculateAndStore(TKF100, SEPTEMBER);

    assertThat(recalculated.version()).isEqualTo(2);
    assertThat(publications.publishedScenarioHistory(TKF100.getIsin()))
        .contains(SEPTEMBER_AT_HALF_A_PERCENT);
  }

  @Test
  void refusesToPublishAMonthAlreadyPublished() {
    var september = calculated(SEPTEMBER);
    publish(SEPTEMBER, september.id());
    var recalculated = calculations.calculateAndStore(TKF100, SEPTEMBER);

    assertThatThrownBy(() -> publish(SEPTEMBER, recalculated.id()))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThat(storedPublications()).isEqualTo(1);
  }

  @Test
  void refusesToPublishACalculationThatALaterVersionOfTheMonthReplaced() {
    var first = calculated(SEPTEMBER);
    var completeSecond = calculated(SEPTEMBER);

    assertThat(completeSecond.version()).isEqualTo(2);
    assertThatThrownBy(() -> publish(SEPTEMBER, first.id()))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThat(storedPublications()).isZero();
  }

  @Test
  void refusesToPublishACalculationThatANewerVersionReplacedAfterTheChecksRan() {
    var first = calculated(SEPTEMBER);
    calculated(SEPTEMBER);
    willReturn(Optional.of(first)).given(calculationRepository).findLatest(TKF100, SEPTEMBER);

    assertThatThrownBy(() -> publish(SEPTEMBER, first.id()))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThat(storedPublications()).isZero();
  }

  @Test
  void theJobLeavesAPublishedMonthAloneEvenWhenABackfillStoredARefusalAfterIt() {
    var september = calculated(SEPTEMBER);
    publish(SEPTEMBER, september.id());
    var gap =
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .filter(
                close ->
                    !close.date().isBefore(LocalDate.of(2023, 3, 1))
                        && !close.date().isAfter(LocalDate.of(2023, 3, 20)))
            .toList();
    jdbcClient
        .sql(
            "DELETE FROM index_values WHERE key = 'MSCI_ACWI'"
                + " AND date BETWEEN DATE '2023-03-01' AND DATE '2023-03-20'")
        .update();
    var refused = calculated(SEPTEMBER);
    fundValues.saveAll(gap);

    calculations.calculateTheMissingMonths();

    assertThat(refused.complete()).isFalse();
    assertThat(
            jdbcClient
                .sql(
                    "SELECT MAX(version) FROM investment_kid_calculation"
                        + " WHERE calculation_month = :month")
                .param("month", SEPTEMBER.atDay(1))
                .query(Integer.class)
                .single())
        .isEqualTo(2);
  }

  @Test
  void refusesToPublishACompleteCalculationThatALaterRefusalReplaced() {
    var complete = calculated(SEPTEMBER);
    jdbcClient
        .sql(
            "DELETE FROM index_values WHERE key = 'MSCI_ACWI'"
                + " AND date BETWEEN DATE '2023-03-01' AND DATE '2023-03-20'")
        .update();
    var refused = calculated(SEPTEMBER);

    assertThat(refused.complete()).isFalse();
    assertThatThrownBy(() -> publish(SEPTEMBER, complete.id()))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThatThrownBy(() -> publish(SEPTEMBER, refused.id()))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThat(storedPublications()).isZero();
  }

  @Test
  void refusesToPublishAnIncompleteCalculation() {
    var june = calculated(YearMonth.of(2026, 6));

    assertThat(june.complete()).isFalse();
    assertThatThrownBy(() -> publish(YearMonth.of(2026, 6), june.id()))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThat(storedPublications()).isZero();
  }

  @Test
  void refusesToPublishACalculationOfAnotherMonthOrFund() {
    var september = calculated(SEPTEMBER);

    assertThatThrownBy(() -> publish(AUGUST, september.id()))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThatThrownBy(
            () ->
                publications.publish(
                    TUK75,
                    new KidPublicationRequest(
                        PUBLISHER, List.of(new MonthToPublish(SEPTEMBER, september.id())))))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThatThrownBy(() -> publish(SEPTEMBER, september.id() + 1000))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThat(storedPublications()).isZero();
  }

  @Test
  void publishesNothingWhenAnyMonthOfTheRequestIsRefused() {
    var august = calculated(AUGUST);
    var september = calculated(SEPTEMBER);
    publish(SEPTEMBER, september.id());

    assertThatThrownBy(
            () ->
                publications.publish(
                    TKF100,
                    new KidPublicationRequest(
                        PUBLISHER,
                        List.of(
                            new MonthToPublish(AUGUST, august.id()),
                            new MonthToPublish(SEPTEMBER, september.id())))))
        .isInstanceOf(KidPublicationRefusedException.class);
    assertThat(storedPublications()).isEqualTo(1);
  }

  @Test
  void refusesARequestNamingAMonthTwice() {
    var september = calculated(SEPTEMBER);

    assertThatThrownBy(
            () ->
                publications.publish(
                    TKF100,
                    new KidPublicationRequest(
                        PUBLISHER,
                        List.of(
                            new MonthToPublish(SEPTEMBER, september.id()),
                            new MonthToPublish(SEPTEMBER, september.id())))))
        .isInstanceOf(InvalidKidRequestException.class);
    assertThat(storedPublications()).isZero();
  }

  @Test
  void aPublishedCalculationCannotBeDeleted() {
    var september = calculated(SEPTEMBER);
    publish(SEPTEMBER, september.id());
    jdbcClient
        .sql("DELETE FROM investment_kid_scenario WHERE calculation_id = :id")
        .param("id", september.id())
        .update();
    jdbcClient
        .sql("DELETE FROM investment_kid_cost WHERE calculation_id = :id")
        .param("id", september.id())
        .update();

    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql("DELETE FROM investment_kid_calculation WHERE id = :id")
                    .param("id", september.id())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aPublicationNamesACalculationOfItsOwnFundAndMonth() {
    var september = calculated(SEPTEMBER);

    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql(
                        """
                        INSERT INTO investment_kid_publication
                          (fund_code, calculation_month, calculation_id, published_by,
                           published_at)
                        VALUES ('TKF100', :month, :calculationId, 'portfolio-manager',
                                CURRENT_TIMESTAMP)
                        """)
                    .param("month", AUGUST.atDay(1))
                    .param("calculationId", september.id())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void servesNoHistoryForAFundWithNoKidOrAnUnknownIsin() {
    assertThat(publications.publishedScenarioHistory(TUK75.getIsin())).isEmpty();
    assertThat(publications.publishedScenarioHistory("EE0000000000")).isEmpty();
  }

  private StoredKidCalculation calculated(YearMonth month) {
    return calculations.calculateAndStore(TKF100, month);
  }

  private List<KidPublication> publish(YearMonth month, long calculationId) {
    return publications.publish(
        TKF100,
        new KidPublicationRequest(PUBLISHER, List.of(new MonthToPublish(month, calculationId))));
  }

  private long storedPublications() {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM investment_kid_publication")
        .query(Long.class)
        .single();
  }

  private static PublishedScenario published(
      YearMonth month, int horizonYears, ScenarioKind kind, String amount, String averageReturn) {
    return new PublishedScenario(
        month, horizonYears, kind, new BigDecimal(amount), new BigDecimal(averageReturn));
  }

  @TestConfiguration
  static class DayAfterTheSeptemberPeriodEnds {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneId.of("Europe/Tallinn"));
    }
  }
}
