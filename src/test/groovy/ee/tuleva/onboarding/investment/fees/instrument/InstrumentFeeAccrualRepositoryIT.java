package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.fees.instrument.AccrualMethod.DAILY_HOLDING;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsSource.NAV_REPORT;
import static ee.tuleva.onboarding.investment.fees.rate.RateStatus.PROVISIONAL;
import static ee.tuleva.onboarding.investment.fees.rate.RateStatus.RESOLVED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.investment.fees.rate.AccrualRate;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(InstrumentFeeAccrualRepository.class)
@Transactional(propagation = NOT_SUPPORTED)
class InstrumentFeeAccrualRepositoryIT {

  private static final String ISIN = "ZZ0000000071";
  private static final LocalDate DAY = LocalDate.of(2026, 11, 22);
  private static final Instant CALCULATED_AT = Instant.parse("2026-11-23T18:00:00Z");

  @Autowired private InstrumentFeeAccrualRepository repository;
  @Autowired private JdbcClient jdbcClient;

  private long providerAgreementId;
  private long versionId;
  private long instrumentFeeId;

  @BeforeEach
  void aSyntheticAgreementCommittedOutsideATestTransaction() {
    jdbcClient
        .sql(
            """
            INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class)
            VALUES (:isin, :isin, 'FUND', 'equity')
            """)
        .param("isin", ISIN)
        .update();
    providerAgreementId =
        inserted(
            "INSERT INTO investment_provider_agreement (created_at) VALUES (CURRENT_TIMESTAMP)");
    versionId =
        inserted(
            """
            INSERT INTO investment_provider_agreement_version
              (provider_agreement_id, accrual_method, payment_period, payer_label, valid_from)
            VALUES (%d, 'DAILY_HOLDING', 'MONTH', 'Synthetic payer', DATE '2026-01-01')
            """
                .formatted(providerAgreementId));
    instrumentFeeId =
        inserted(
            """
            INSERT INTO investment_instrument_fee
              (isin, published_ocf, rebate_kind, rebate_terms, valid_from, provider_agreement_id)
            VALUES ('%s', 0.00070000, 'NONE', '{}', DATE '2026-01-01', %d)
            """
                .formatted(ISIN, providerAgreementId));
  }

  @AfterEach
  void removeWhatTheTestCommitted() {
    jdbcClient
        .sql("DELETE FROM investment_instrument_fee_accrual WHERE isin = :isin")
        .param("isin", ISIN)
        .update();
    jdbcClient
        .sql("DELETE FROM investment_instrument_fee_rate WHERE isin = :isin")
        .param("isin", ISIN)
        .update();
    jdbcClient
        .sql("DELETE FROM investment_instrument_fee WHERE isin = :isin")
        .param("isin", ISIN)
        .update();
    jdbcClient
        .sql("DELETE FROM investment_provider_agreement_version WHERE id = :id")
        .param("id", versionId)
        .update();
    jdbcClient
        .sql("DELETE FROM investment_provider_agreement WHERE id = :id")
        .param("id", providerAgreementId)
        .update();
    jdbcClient
        .sql("DELETE FROM instrument_reference WHERE isin = :isin")
        .param("isin", ISIN)
        .update();
  }

  @Test
  void aReplaceThatFailsPartWayKeepsTheFundsEarlierAccrualsForTheDay() {
    repository.replaceTheFundsDay(TUK75, DAY, List.of(accrual(TUK75, "0.00050000")), CALCULATED_AT);

    assertThatThrownBy(
            () ->
                repository.replaceTheFundsDay(
                    TUK75,
                    DAY,
                    List.of(accrual(TUK75, "0.00040000"), accrual(TUK75, "0.00040000")),
                    CALCULATED_AT))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(storedRebateRates()).containsExactly(new BigDecimal("0.00050000"));
  }

  @Test
  void replacingOneFundsDayLeavesTheOtherFundsAccrualsForTheDay() {
    repository.replaceTheFundsDay(TUK75, DAY, List.of(accrual(TUK75, "0.00050000")), CALCULATED_AT);
    repository.replaceTheFundsDay(TUK00, DAY, List.of(accrual(TUK00, "0.00030000")), CALCULATED_AT);

    repository.replaceTheFundsDay(TUK75, DAY, List.of(), CALCULATED_AT);

    assertThat(storedRebateRates()).containsExactly(new BigDecimal("0.00030000"));
  }

  @Test
  void aProvisionalAccrualIsOutOfStepWithItsMonthsRates() {
    repository.replaceTheFundsDay(TUK75, DAY, List.of(accrual(TUK75, "0.00050000")), CALCULATED_AT);

    assertThat(repository.firstDayOutOfStepWithItsMonthsRates(DAY.minusDays(30), DAY))
        .contains(DAY);
    assertThat(repository.firstDayOutOfStepWithItsMonthsRates(DAY.plusDays(1), DAY.plusDays(30)))
        .isEmpty();
  }

  @Test
  void anAccrualOnTheMonthsNewestRateIsInStep() {
    var rateId = aRateRow();
    repository.replaceTheFundsDay(
        TUK75, DAY, List.of(resolvedAccrual(TUK75, rateId)), CALCULATED_AT);

    assertThat(repository.firstDayOutOfStepWithItsMonthsRates(DAY.minusDays(30), DAY)).isEmpty();
  }

  @Test
  void anAccrualOnARateTheMonthHasSinceReplacedIsOutOfStep() {
    var supersededRateId = aRateRow();
    repository.replaceTheFundsDay(
        TUK75, DAY, List.of(resolvedAccrual(TUK75, supersededRateId)), CALCULATED_AT);
    aRateRow();

    assertThat(repository.firstDayOutOfStepWithItsMonthsRates(DAY.minusDays(30), DAY))
        .contains(DAY);
  }

  private InstrumentFeeAccrual resolvedAccrual(TulevaFund fund, long rateId) {
    return new InstrumentFeeAccrual(
        fund,
        DAY,
        new AccrualRate(
            instrumentFeeId,
            ISIN,
            providerAgreementId,
            RESOLVED,
            rateId,
            new BigDecimal("0.00050000"),
            ZERO),
        new ProviderAgreementVersion(versionId, providerAgreementId, DAILY_HOLDING),
        new Holding(new BigDecimal("1000.000"), DAY, NAV_REPORT, null),
        new InstrumentPrice(new BigDecimal("12.34"), DAY, "BLACKROCK"));
  }

  private long aRateRow() {
    return inserted(
        """
        INSERT INTO investment_instrument_fee_rate
          (isin, period_start, period_end, published_ocf, rebate_rate, invoiced_fee_rate,
           net_ocf, rate_basis, rebate_kind, instrument_fee_id)
        VALUES ('%s', DATE '2026-11-01', DATE '2026-11-30', 0.00070000, 0.00050000, 0.00000000,
                0.00020000, 'AGREEMENT', 'FIXED', %d)
        """
            .formatted(ISIN, instrumentFeeId));
  }

  private InstrumentFeeAccrual accrual(TulevaFund fund, String rebateRate) {
    return new InstrumentFeeAccrual(
        fund,
        DAY,
        new AccrualRate(
            instrumentFeeId,
            ISIN,
            providerAgreementId,
            PROVISIONAL,
            null,
            new BigDecimal(rebateRate),
            ZERO),
        new ProviderAgreementVersion(versionId, providerAgreementId, DAILY_HOLDING),
        new Holding(new BigDecimal("1000.000"), DAY, NAV_REPORT, null),
        new InstrumentPrice(new BigDecimal("12.34"), DAY, "BLACKROCK"));
  }

  private List<BigDecimal> storedRebateRates() {
    return jdbcClient
        .sql(
            "SELECT rebate_rate FROM investment_instrument_fee_accrual WHERE isin = :isin"
                + " ORDER BY fund_code")
        .param("isin", ISIN)
        .query(BigDecimal.class)
        .list();
  }

  private long inserted(String sql) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient.sql(sql).update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey()).longValue();
  }
}
