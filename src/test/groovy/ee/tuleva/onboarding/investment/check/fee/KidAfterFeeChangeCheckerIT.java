package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.KID_AFTER_FEE_CHANGE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.ocf.KidsInForce;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({
  FeeRateRepository.class,
  PublicHolidays.class,
  KidAfterFeeChangeChecker.class,
  KidAfterFeeChangeCheckerIT.KidRecord.class
})
class KidAfterFeeChangeCheckerIT {

  private static final String SUNDAY_FEE_RISE = "2026-12-06";
  private static final LocalDate THE_FRIDAY_BEFORE_THE_WARNING = LocalDate.parse("2026-11-20");
  private static final LocalDate TEN_WORKING_DAYS_BEFORE_THE_RISE = LocalDate.parse("2026-11-23");
  private static final LocalDate THE_MONDAY_AFTER_THE_RISE = LocalDate.parse("2026-12-07");
  private static final LocalDate THE_WEDNESDAY_AFTER_THE_RISE = LocalDate.parse("2026-12-09");

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.investment.fees.ocf",
      useDefaultFilters = false,
      includeFilters = {
        @ComponentScan.Filter(type = ASSIGNABLE_TYPE, classes = KidsInForce.class),
        @ComponentScan.Filter(type = REGEX, pattern = ".*\\.OcfPublicationRepository")
      })
  static class KidRecord {}

  @Autowired private KidAfterFeeChangeChecker checker;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_fee_rate").update();
    jdbcClient.sql("DELETE FROM investment_ocf_publication").update();
  }

  @Test
  void staysQuietUntilTenWorkingDaysBeforeAFeeChange() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-03-19");

    assertThat(checker.check(TUV100, THE_FRIDAY_BEFORE_THE_WARNING)).containsExactly(pass(TUV100));
  }

  @Test
  void warnsFromTenWorkingDaysBeforeAFeeChangeThatHasNoKidRecordedWithIt() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-03-19");

    assertThat(checker.check(TUV100, TEN_WORKING_DAYS_BEFORE_THE_RISE))
        .containsExactly(
            finding(
                TUV100,
                WARNING,
                SUNDAY_FEE_RISE,
                "Management fee 0.202% from 2026-12-06 has no KID taking effect with it (the KID in"
                    + " force on 2026-12-06 is from 2026-03-19; one from 2026-11-23 or later"
                    + " counts). Record the KID for this fee with ocf.publish_given"));
  }

  @Test
  void aKidRecordedAheadOfTheFeeChangeClearsTheWarning() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-03-19");
    kid(TUV100, "2026-12-04");

    assertThat(checker.check(TUV100, TEN_WORKING_DAYS_BEFORE_THE_RISE))
        .containsExactly(pass(TUV100));
  }

  @Test
  void aKidFromBeforeTheTenWorkingDaysDoesNotCountForTheFeeChange() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-11-20");

    assertThat(checker.check(TUV100, TEN_WORKING_DAYS_BEFORE_THE_RISE))
        .containsExactly(
            finding(
                TUV100,
                WARNING,
                SUNDAY_FEE_RISE,
                "Management fee 0.202% from 2026-12-06 has no KID taking effect with it (the KID in"
                    + " force on 2026-12-06 is from 2026-11-20; one from 2026-11-23 or later"
                    + " counts). Record the KID for this fee with ocf.publish_given"));
  }

  @Test
  void aKidTakingEffectOnTheFirstDayOfTheTenWorkingDaysCountsForTheFeeChange() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-11-23");

    assertThat(checker.check(TUV100, TEN_WORKING_DAYS_BEFORE_THE_RISE))
        .containsExactly(pass(TUV100));
  }

  @Test
  void aKidTakingEffectAfterTheFeeChangeDoesNotClearTheWarning() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-03-19");
    kid(TUV100, "2026-12-09");

    assertThat(checker.check(TUV100, TEN_WORKING_DAYS_BEFORE_THE_RISE))
        .containsExactly(
            finding(
                TUV100,
                WARNING,
                SUNDAY_FEE_RISE,
                "Management fee 0.202% from 2026-12-06 has no KID taking effect with it (the KID in"
                    + " force on 2026-12-06 is from 2026-03-19; one from 2026-11-23 or later"
                    + " counts). Record the KID for this fee with ocf.publish_given"));
  }

  @Test
  void failsFromTheDayTheFeeChangesWhileNoKidIsRecordedWithIt() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-03-19");

    assertThat(checker.check(TUV100, THE_MONDAY_AFTER_THE_RISE))
        .containsExactly(
            finding(
                TUV100,
                FAIL,
                SUNDAY_FEE_RISE,
                "Management fee 0.202% from 2026-12-06 has no KID taking effect with it (the KID in"
                    + " force on 2026-12-07 is from 2026-03-19; one from 2026-11-23 or later"
                    + " counts). Record the KID for this fee with ocf.publish_given"));
  }

  @Test
  void aKidTakingEffectTheFridayBeforeASundayFeeChangeClearsTheFailure() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-03-19");
    kid(TUV100, "2026-12-04");

    assertThat(checker.check(TUV100, THE_MONDAY_AFTER_THE_RISE)).containsExactly(pass(TUV100));
  }

  @Test
  void aKidTakingEffectAfterTheFeeChangeClearsTheFailureOnlyFromItsOwnDate() {
    tuv100FeeRisesOnASunday();
    kid(TUV100, "2026-03-19");
    kid(TUV100, "2026-12-09");

    assertThat(checker.check(TUV100, THE_MONDAY_AFTER_THE_RISE))
        .extracting(FeeCheckFinding::severity)
        .containsExactly(FAIL);
    assertThat(checker.check(TUV100, THE_WEDNESDAY_AFTER_THE_RISE)).containsExactly(pass(TUV100));
  }

  @Test
  void passesWhenTheKidTakesEffectTheDayTheFeeChanges() {
    rate(TKF100, "0.00152000", "2026-02-02", "2026-10-28");
    rate(TKF100, "0.00178000", "2026-10-29", null);
    kid(TKF100, "2026-10-29");

    assertThat(checker.check(TKF100, LocalDate.parse("2026-10-30"))).containsExactly(pass(TKF100));
  }

  @Test
  void aFeeCutAsksForItsKidLikeARise() {
    rate(TUK75, "0.00205000", "2026-02-27", "2027-02-28");
    rate(TUK75, "0.00190000", "2027-03-01", null);
    kid(TUK75, "2026-03-19");

    assertThat(checker.check(TUK75, LocalDate.parse("2027-02-12")))
        .containsExactly(
            finding(
                TUK75,
                WARNING,
                "2027-03-01",
                "Management fee 0.19% from 2027-03-01 has no KID taking effect with it (the KID in"
                    + " force on 2027-03-01 is from 2026-03-19; one from 2027-02-12 or later"
                    + " counts). Record the KID for this fee with ocf.publish_given"));
  }

  @Test
  void failsWhenTheFundHasNoKidRecordedAtAll() {
    rate(TUK00, "0.00163000", "2026-02-27", null);

    assertThat(checker.check(TUK00, LocalDate.parse("2026-10-30")))
        .containsExactly(
            finding(
                TUK00,
                FAIL,
                "2026-02-27",
                "Management fee 0.163% from 2026-02-27 has no KID taking effect with it (no KID in"
                    + " force on 2026-10-30 in investment_ocf_publication; one from 2026-02-12"
                    + " or later counts). Record the KID for this fee with ocf.publish_given"));
  }

  @Test
  void reportsTheFeeInForceAndTheUpcomingOneWhenNeitherHasItsKid() {
    rate(TUV100, "0.00179000", "2026-09-18", "2026-12-05");
    rate(TUV100, "0.00202000", SUNDAY_FEE_RISE, null);
    kid(TUV100, "2026-03-19");

    assertThat(checker.check(TUV100, TEN_WORKING_DAYS_BEFORE_THE_RISE))
        .extracting(FeeCheckFinding::severity, FeeCheckFinding::identifiers)
        .containsExactly(
            tuple(FAIL, List.of("feeFrom=2026-09-18")),
            tuple(WARNING, List.of("feeFrom=2026-12-06")));
  }

  @Test
  void doesNotRunForAFundWithNoManagementFeeRate() {
    assertThat(checker.check(TUK00, LocalDate.parse("2026-10-30")))
        .containsExactly(noFeeRateInForceOn(TUK00, "2026-10-30"));
  }

  @Test
  void doesNotRunWhileNoManagementFeeRateIsInForceEvenWithOneComingLater() {
    rate(TUK00, "0.00163000", "2026-02-27", "2026-10-28");
    rate(TUK00, "0.00150000", "2026-12-01", null);
    kid(TUK00, "2026-03-19");

    assertThat(checker.check(TUK00, LocalDate.parse("2026-10-30")))
        .containsExactly(noFeeRateInForceOn(TUK00, "2026-10-30"));
  }

  private void tuv100FeeRisesOnASunday() {
    rate(TUV100, "0.00179000", "2026-02-27", "2026-12-05");
    rate(TUV100, "0.00202000", SUNDAY_FEE_RISE, null);
  }

  private void rate(
      TulevaFund fund, String annualRate, String validFrom, @Nullable String validTo) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate
              (fund_code, fee_type, annual_rate, valid_from, valid_to, created_by)
            VALUES (:fundCode, 'MANAGEMENT', :annualRate, :validFrom, :validTo, 'TEST')
            """)
        .param("fundCode", fund.name())
        .param("annualRate", new BigDecimal(annualRate))
        .param("validFrom", LocalDate.parse(validFrom))
        .param("validTo", validTo == null ? null : LocalDate.parse(validTo))
        .update();
  }

  private void kid(TulevaFund fund, String validFrom) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_publication
              (fund_code, valid_from, published_ocf, published_decimals, basis, basis_reason,
               published_in, published_by)
            VALUES (:fundCode, :validFrom, 0.0028, 2, 'MONTHLY_ESTIMATE', 'the KID in force',
                    :publishedIn, 'TEST')
            """)
        .param("fundCode", fund.getCode())
        .param("validFrom", LocalDate.parse(validFrom))
        .param("publishedIn", "KID " + validFrom)
        .update();
  }

  private static FeeCheckFinding noFeeRateInForceOn(TulevaFund fund, String date) {
    return new FeeCheckFinding(
        fund,
        KID_AFTER_FEE_CHANGE,
        MANAGEMENT,
        NOT_RUN,
        "No management fee rate in investment_fee_rate in force on " + date,
        null,
        List.of(),
        Map.of());
  }

  private static FeeCheckFinding pass(TulevaFund fund) {
    return FeeCheckFinding.pass(fund, KID_AFTER_FEE_CHANGE, MANAGEMENT);
  }

  private static FeeCheckFinding finding(
      TulevaFund fund, FeeCheckSeverity severity, String feeFrom, String message) {
    return new FeeCheckFinding(
        fund,
        KID_AFTER_FEE_CHANGE,
        MANAGEMENT,
        severity,
        message,
        null,
        List.of("feeFrom=" + feeFrom),
        Map.of());
  }
}
