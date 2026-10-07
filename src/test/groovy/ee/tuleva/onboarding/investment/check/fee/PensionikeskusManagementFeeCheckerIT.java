package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.PENSIONIKESKUS_MANAGEMENT_FEE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({FeeRateRepository.class, PublicHolidays.class, PensionikeskusManagementFeeChecker.class})
class PensionikeskusManagementFeeCheckerIT {

  private static final LocalDate EFFECTIVE_THURSDAY = LocalDate.of(2026, 10, 29);
  private static final LocalDate FRIDAY_AFTER = LocalDate.of(2026, 10, 30);
  private static final LocalDate WEDNESDAY_BEFORE = LocalDate.of(2026, 10, 28);

  @Autowired private PensionikeskusManagementFeeChecker checker;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private TestEntityManager entityManager;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_fee_rate").update();
  }

  @Test
  void passesWhenPensionikeskusShowsTheRateInForce() {
    pensionikeskusShows("0.00163");
    rate("0.00163000", "2026-02-27", null);

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_MANAGEMENT_FEE, MANAGEMENT));
  }

  @Test
  void failsWhenPensionikeskusShowsADifferentRateThanTheOneInForce() {
    pensionikeskusShows("0.0017");
    rate("0.00163000", "2026-02-27", null);

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(
            finding(
                FAIL,
                List.of("pensionikeskus=0.0017", "inForce=0.00163"),
                "Pensionikeskus shows a management fee of 0.0017, but investment_fee_rate has"
                    + " 0.00163 in force on 2026-10-30"));
  }

  @Test
  void givesPensionikeskusOneWorkingDayToShowANewRate() {
    pensionikeskusShows("0.00163");
    rate("0.00163000", "2026-02-27", "2026-10-28");
    rate("0.00150000", "2026-10-29", null);

    assertThat(checker.check(TUK00, EFFECTIVE_THURSDAY))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_MANAGEMENT_FEE, MANAGEMENT));
  }

  @Test
  void failsOnceTheWorkingDayOfGraceHasPassedWithoutPensionikeskusShowingTheNewRate() {
    pensionikeskusShows("0.00163");
    rate("0.00163000", "2026-02-27", "2026-10-28");
    rate("0.00150000", "2026-10-29", null);

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(
            finding(
                FAIL,
                List.of("pensionikeskus=0.00163", "inForce=0.0015"),
                "Pensionikeskus shows a management fee of 0.00163, but investment_fee_rate has"
                    + " 0.0015 in force on 2026-10-30"));
  }

  @Test
  void aMissedFeeChangeFailsUntilPensionikeskusShowsTheNewRateAndThenClears() {
    pensionikeskusShows("0.00163");
    rate("0.00163000", "2026-02-27", "2026-10-28");
    rate("0.00150000", "2026-10-29", null);
    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .extracting(FeeCheckFinding::severity)
        .containsExactly(FAIL);

    theNextSyncFromPensionikeskusShows("0.0015");

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_MANAGEMENT_FEE, MANAGEMENT));
  }

  @Test
  void aNewRateThatPensionikeskusShowsAWorkingDayEarlyIsNotAMismatch() {
    pensionikeskusShows("0.0015");
    rate("0.00163000", "2026-02-27", "2026-10-28");
    rate("0.00150000", "2026-10-29", null);

    assertThat(checker.check(TUK00, WEDNESDAY_BEFORE))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_MANAGEMENT_FEE, MANAGEMENT));
  }

  @Test
  void cannotRunWithoutARateInForceAndSaysSoRatherThanPassing() {
    pensionikeskusShows("0.00163");

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(
            finding(
                NOT_RUN,
                List.of(),
                "No management fee rate in investment_fee_rate on 2026-10-30 to compare"
                    + " Pensionikeskus' with"));
  }

  @Test
  void theSavingsFundIsNotOnPensionikeskusSoItHasNoFinding() {
    assertThat(checker.check(TKF100, FRIDAY_AFTER)).isEmpty();
  }

  private void pensionikeskusShows(String rate) {
    jdbcClient
        .sql("UPDATE fund SET management_fee_rate = :rate WHERE isin = :isin")
        .param("rate", new BigDecimal(rate))
        .param("isin", TUK00.getIsin())
        .update();
  }

  private void theNextSyncFromPensionikeskusShows(String rate) {
    pensionikeskusShows(rate);
    entityManager.clear();
  }

  private void rate(String annualRate, String validFrom, @Nullable String validTo) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, valid_to)
            VALUES ('TUK00', 'MANAGEMENT', :rate, :validFrom, :validTo)
            """)
        .param("rate", new BigDecimal(annualRate))
        .param("validFrom", LocalDate.parse(validFrom))
        .param("validTo", validTo == null ? null : LocalDate.parse(validTo))
        .update();
  }

  private static FeeCheckFinding finding(
      FeeCheckSeverity severity, List<String> identifiers, String message) {
    return new FeeCheckFinding(
        TUK00,
        PENSIONIKESKUS_MANAGEMENT_FEE,
        MANAGEMENT,
        severity,
        message,
        null,
        identifiers,
        Map.of());
  }
}
