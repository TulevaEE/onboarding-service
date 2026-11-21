package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.config.CacheConfiguration;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.OngoingChargesFigures;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import({CacheConfiguration.class, SavingsFundOngoingChargesSyncIntegrationTest.KidRecord.class})
@TestPropertySource(properties = "spring.cache.type=simple")
class SavingsFundOngoingChargesSyncIntegrationTest {

  private static final LocalDate THE_DAY_BEFORE_THE_NEW_KID = LocalDate.parse("2026-10-28");
  private static final LocalDate THE_DAY_THE_NEW_KID_TAKES_EFFECT = LocalDate.parse("2026-10-29");

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.investment.fees.ocf",
      useDefaultFilters = false,
      includeFilters = {
        @ComponentScan.Filter(type = ASSIGNABLE_TYPE, classes = OngoingChargesFigures.class),
        @ComponentScan.Filter(type = REGEX, pattern = ".*\\.OcfPublicationRepository")
      })
  static class KidRecord {}

  @Autowired private OngoingChargesFigures ongoingChargesFigures;
  @Autowired private FundRepository fundRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private TestEntityManager entityManager;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_ocf_publication").update();
    fundTableShows(TKF100, "0.00400000");
    fundTableShows(TUK00, "0.00400000");
  }

  @Test
  void theSavingsFundsFundTableFigureBecomesTheFigureTheKidInForcePrints() {
    kid(TKF100, "2026-01-12", "0.00276000", 2);

    syncOn(THE_DAY_THE_NEW_KID_TAKES_EFFECT);

    assertThat(fundTableFigure(TKF100)).isEqualByComparingTo("0.0028");
  }

  @Test
  void aKidPrintingOneDecimalGivesTheFundTableThatOneDecimalFigure() {
    kid(TKF100, "2026-01-12", "0.00276000", 1);

    syncOn(THE_DAY_THE_NEW_KID_TAKES_EFFECT);

    assertThat(fundTableFigure(TKF100)).isEqualByComparingTo("0.003");
  }

  @Test
  void theDayBeforeANewKidTakesEffectTheFundTableKeepsTheOldKidsFigure() {
    kid(TKF100, "2026-01-12", "0.00280000", 2);
    kid(TKF100, "2026-10-29", "0.00300000", 2);

    syncOn(THE_DAY_BEFORE_THE_NEW_KID);

    assertThat(fundTableFigure(TKF100)).isEqualByComparingTo("0.0028");
  }

  @Test
  void aNewKidsFigureReachesTheFundTableOnItsFirstDay() {
    kid(TKF100, "2026-01-12", "0.00280000", 2);
    kid(TKF100, "2026-10-29", "0.00300000", 2);

    syncOn(THE_DAY_THE_NEW_KID_TAKES_EFFECT);

    assertThat(fundTableFigure(TKF100)).isEqualByComparingTo("0.0030");
  }

  @Test
  void withNoKidInForceYetTheFundTableKeepsItsFigure() {
    kid(TKF100, "2026-10-29", "0.00300000", 2);

    syncOn(THE_DAY_BEFORE_THE_NEW_KID);

    assertThat(fundTableFigure(TKF100)).isEqualByComparingTo("0.0040");
  }

  @Test
  void anOutOfBoundsKidFigureIsSkippedAndTheFundTableKeepsItsFigure() {
    kid(TKF100, "2026-01-12", "0.03000000", 2);

    syncOn(THE_DAY_THE_NEW_KID_TAKES_EFFECT);

    assertThat(fundTableFigure(TKF100)).isEqualByComparingTo("0.0040");
  }

  @Test
  void anEditToTheFundRowAfterItWasCachedSurvivesTheSync() {
    kid(TKF100, "2026-01-12", "0.00280000", 2);
    fundRepository.findByIsin(TKF100.getIsin());
    entityManager.clear();
    jdbcClient
        .sql("UPDATE fund SET management_fee_rate = :value WHERE isin = :isin")
        .param("value", new BigDecimal("0.00178"))
        .param("isin", TKF100.getIsin())
        .update();

    syncOn(THE_DAY_THE_NEW_KID_TAKES_EFFECT);

    assertThat(fundTableManagementFee(TKF100)).isEqualByComparingTo("0.00178");
    assertThat(fundTableFigure(TKF100)).isEqualByComparingTo("0.0028");
  }

  @Test
  void aPensionFundKeepsPensionikeskusFigureWhileTheSavingsFundTakesItsKidsFigure() {
    kid(TUK00, "2026-03-19", "0.00280000", 2);
    kid(TKF100, "2026-01-12", "0.00280000", 2);

    syncOn(THE_DAY_THE_NEW_KID_TAKES_EFFECT);

    assertThat(fundTableFigure(TUK00)).isEqualByComparingTo("0.0040");
    assertThat(fundTableFigure(TKF100)).isEqualByComparingTo("0.0028");
  }

  private void syncOn(LocalDate date) {
    new SavingsFundOngoingChargesSync(
            ongoingChargesFigures, fundRepository, new FundFeeUpdater(fundRepository))
        .syncToTheKidsInForceOn(date);
  }

  private void fundTableShows(TulevaFund fund, String figure) {
    jdbcClient
        .sql("UPDATE fund SET ongoing_charges_figure = :figure WHERE isin = :isin")
        .param("figure", new BigDecimal(figure))
        .param("isin", fund.getIsin())
        .update();
  }

  private BigDecimal fundTableFigure(TulevaFund fund) {
    entityManager.flush();
    return jdbcClient
        .sql("SELECT ongoing_charges_figure FROM fund WHERE isin = :isin")
        .param("isin", fund.getIsin())
        .query(BigDecimal.class)
        .single();
  }

  private BigDecimal fundTableManagementFee(TulevaFund fund) {
    entityManager.flush();
    return jdbcClient
        .sql("SELECT management_fee_rate FROM fund WHERE isin = :isin")
        .param("isin", fund.getIsin())
        .query(BigDecimal.class)
        .single();
  }

  private void kid(TulevaFund fund, String validFrom, String publishedOcf, int publishedDecimals) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_publication
              (fund_code, valid_from, published_ocf, published_decimals, basis, basis_reason,
               published_in, published_by)
            VALUES (:fundCode, :validFrom, :publishedOcf, :publishedDecimals, 'MONTHLY_ESTIMATE',
                    'the KID in force', :publishedIn, 'portfolio-manager')
            """)
        .param("fundCode", fund.getCode())
        .param("validFrom", LocalDate.parse(validFrom))
        .param("publishedOcf", new BigDecimal(publishedOcf))
        .param("publishedDecimals", publishedDecimals)
        .param("publishedIn", "KID " + validFrom)
        .update();
  }
}
