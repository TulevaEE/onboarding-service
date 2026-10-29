package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;

import ee.tuleva.onboarding.config.CacheConfiguration;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.ManagementFeeRates;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
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
@Import({CacheConfiguration.class, SavingsFundManagementFeeSyncIntegrationTest.RateTable.class})
@TestPropertySource(properties = "spring.cache.type=simple")
class SavingsFundManagementFeeSyncIntegrationTest {

  private static final LocalDate THE_DAY_BEFORE_THE_CHANGE = LocalDate.parse("2026-06-03");
  private static final LocalDate THE_DAY_OF_THE_CHANGE = LocalDate.parse("2026-06-04");

  @TestConfiguration
  @ComponentScan(
      basePackageClasses = FeeRateRepository.class,
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = ASSIGNABLE_TYPE,
              classes = {FeeRateRepository.class, ManagementFeeRates.class}))
  static class RateTable {}

  @Autowired private ManagementFeeRates managementFeeRates;
  @Autowired private FundRepository fundRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private TestEntityManager entityManager;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_fee_rate").update();
    fundTableShows(TKF100, "0.00400");
    fundTableShows(TUK00, "0.00400");
  }

  @Test
  void theSavingsFundsFundTableRateBecomesTheRateInForce() {
    managementFeeRate(TKF100, "0.00450000", "2026-02-02", null);

    syncOn(THE_DAY_OF_THE_CHANGE);

    assertThat(fundTableRate(TKF100)).isEqualByComparingTo("0.00450");
  }

  @Test
  void theDayBeforeARateChangeTheFundTableKeepsTheOldRate() {
    managementFeeRate(TKF100, "0.00450000", "2026-02-02", "2026-06-03");
    managementFeeRate(TKF100, "0.00500000", "2026-06-04", null);

    syncOn(THE_DAY_BEFORE_THE_CHANGE);

    assertThat(fundTableRate(TKF100)).isEqualByComparingTo("0.00450");
  }

  @Test
  void aRateChangeReachesTheFundTableOnItsFirstDay() {
    managementFeeRate(TKF100, "0.00450000", "2026-02-02", "2026-06-03");
    managementFeeRate(TKF100, "0.00500000", "2026-06-04", null);

    syncOn(THE_DAY_OF_THE_CHANGE);

    assertThat(fundTableRate(TKF100)).isEqualByComparingTo("0.00500");
  }

  @Test
  void withNoRateInForceYetTheFundTableKeepsItsRate() {
    managementFeeRate(TKF100, "0.00500000", "2026-06-04", null);

    syncOn(THE_DAY_BEFORE_THE_CHANGE);

    assertThat(fundTableRate(TKF100)).isEqualByComparingTo("0.00400");
  }

  @Test
  void anOutOfBoundsRateInTheRateTableIsSkippedAndTheFundTableKeepsItsRate() {
    managementFeeRate(TKF100, "0.03000000", "2026-02-02", null);

    syncOn(THE_DAY_OF_THE_CHANGE);

    assertThat(fundTableRate(TKF100)).isEqualByComparingTo("0.00400");
  }

  @Test
  void anEditToTheFundRowAfterItWasCachedSurvivesTheSync() {
    managementFeeRate(TKF100, "0.00450000", "2026-02-02", null);
    fundRepository.findByIsin(TKF100.getIsin());
    entityManager.clear();
    jdbcClient
        .sql("UPDATE fund SET ongoing_charges_figure = :value WHERE isin = :isin")
        .param("value", new BigDecimal("0.00700"))
        .param("isin", TKF100.getIsin())
        .update();

    syncOn(THE_DAY_OF_THE_CHANGE);

    assertThat(fundTableOngoingCharges(TKF100)).isEqualByComparingTo("0.00700");
    assertThat(fundTableRate(TKF100)).isEqualByComparingTo("0.00450");
  }

  @Test
  void aPensionFundKeepsPensionikeskusRateWhileTheSavingsFundTakesTheRateInForce() {
    managementFeeRate(TUK00, "0.00500000", "2026-02-02", null);
    managementFeeRate(TKF100, "0.00450000", "2026-02-02", null);

    syncOn(THE_DAY_OF_THE_CHANGE);

    assertThat(fundTableRate(TUK00)).isEqualByComparingTo("0.00400");
    assertThat(fundTableRate(TKF100)).isEqualByComparingTo("0.00450");
  }

  private void syncOn(LocalDate date) {
    new SavingsFundManagementFeeSync(
            managementFeeRates, fundRepository, new FundFeeUpdater(fundRepository))
        .syncToTheRatesInForceOn(date);
  }

  private void fundTableShows(TulevaFund fund, String rate) {
    jdbcClient
        .sql("UPDATE fund SET management_fee_rate = :rate WHERE isin = :isin")
        .param("rate", new BigDecimal(rate))
        .param("isin", fund.getIsin())
        .update();
  }

  private BigDecimal fundTableRate(TulevaFund fund) {
    entityManager.flush();
    return jdbcClient
        .sql("SELECT management_fee_rate FROM fund WHERE isin = :isin")
        .param("isin", fund.getIsin())
        .query(BigDecimal.class)
        .single();
  }

  private BigDecimal fundTableOngoingCharges(TulevaFund fund) {
    entityManager.flush();
    return jdbcClient
        .sql("SELECT ongoing_charges_figure FROM fund WHERE isin = :isin")
        .param("isin", fund.getIsin())
        .query(BigDecimal.class)
        .single();
  }

  private void managementFeeRate(
      TulevaFund fund, String annualRate, String validFrom, @Nullable String validTo) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, valid_to)
            VALUES (:fundCode, 'MANAGEMENT', :rate, :validFrom, :validTo)
            """)
        .param("fundCode", fund.name())
        .param("rate", new BigDecimal(annualRate))
        .param("validFrom", LocalDate.parse(validFrom))
        .param("validTo", validTo == null ? null : LocalDate.parse(validTo))
        .update();
  }
}
