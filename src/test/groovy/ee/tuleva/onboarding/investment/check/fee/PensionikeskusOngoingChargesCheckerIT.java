package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.PENSIONIKESKUS_ONGOING_CHARGES;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.OngoingChargesFigures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({
  PublicHolidays.class,
  PensionikeskusOngoingChargesChecker.class,
  PensionikeskusOngoingChargesCheckerIT.KidRecord.class
})
class PensionikeskusOngoingChargesCheckerIT {

  private static final LocalDate EFFECTIVE_THURSDAY = LocalDate.of(2026, 10, 29);
  private static final LocalDate FRIDAY_AFTER = LocalDate.of(2026, 10, 30);
  private static final LocalDate WEDNESDAY_BEFORE = LocalDate.of(2026, 10, 28);
  private static final LocalDate MONDAY_AFTER_A_SATURDAY_KID = LocalDate.of(2026, 11, 2);
  private static final LocalDate TUESDAY_AFTER_A_SATURDAY_KID = LocalDate.of(2026, 11, 3);

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.investment.fees.ocf",
      useDefaultFilters = false,
      includeFilters = {
        @ComponentScan.Filter(type = ASSIGNABLE_TYPE, classes = OngoingChargesFigures.class),
        @ComponentScan.Filter(type = REGEX, pattern = ".*\\.OcfPublicationRepository")
      })
  static class KidRecord {}

  @Autowired private PensionikeskusOngoingChargesChecker checker;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private TestEntityManager entityManager;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_ocf_publication").update();
  }

  @Test
  void passesWhenPensionikeskusShowsTheFigureTheKidInForcePrints() {
    pensionikeskusShows("0.0028");
    kid("2026-03-19", "0.00276000");

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_ONGOING_CHARGES, ALL));
  }

  @Test
  void failsWhenPensionikeskusShowsADifferentFigureThanTheKidInForce() {
    pensionikeskusShows("0.0029");
    kid("2026-03-19", "0.00280000");

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(
            finding(
                FAIL,
                List.of("pensionikeskus=0.0029", "kid=0.0028"),
                "Pensionikeskus shows ongoing charges of 0.29%, but the KID in force on"
                    + " 2026-10-30 prints 0.28%"));
  }

  @Test
  void givesPensionikeskusOneWorkingDayToShowANewKidsFigure() {
    pensionikeskusShows("0.0028");
    kid("2026-03-19", "0.00280000");
    kid("2026-10-29", "0.00310000");

    assertThat(checker.check(TUK00, EFFECTIVE_THURSDAY))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_ONGOING_CHARGES, ALL));
  }

  @Test
  void failsOnceTheWorkingDayOfGraceHasPassedWithoutPensionikeskusShowingTheNewKidsFigure() {
    pensionikeskusShows("0.0028");
    kid("2026-03-19", "0.00280000");
    kid("2026-10-29", "0.00310000");

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(
            finding(
                FAIL,
                List.of("pensionikeskus=0.0028", "kid=0.0031"),
                "Pensionikeskus shows ongoing charges of 0.28%, but the KID in force on"
                    + " 2026-10-30 prints 0.31%"));
  }

  @Test
  void aMissedKidChangeFailsUntilPensionikeskusShowsTheNewFigureAndThenClears() {
    pensionikeskusShows("0.0028");
    kid("2026-03-19", "0.00280000");
    kid("2026-10-29", "0.00310000");
    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .extracting(FeeCheckFinding::severity)
        .containsExactly(FAIL);

    theNextSyncFromPensionikeskusShows("0.0031");

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_ONGOING_CHARGES, ALL));
  }

  @Test
  void aNewKidsFigureThatPensionikeskusShowsAWorkingDayEarlyIsNotAMismatch() {
    pensionikeskusShows("0.0031");
    kid("2026-03-19", "0.00280000");
    kid("2026-10-29", "0.00310000");

    assertThat(checker.check(TUK00, WEDNESDAY_BEFORE))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_ONGOING_CHARGES, ALL));
  }

  @Test
  void aPensionikeskusFigureMorePreciseThanTheKidPassesWhenItRoundsToTheKidsFigure() {
    pensionikeskusShows("0.00276");
    kid("2026-03-19", "0.00280000");

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_ONGOING_CHARGES, ALL));
  }

  @Test
  void aKidTakingEffectOnASaturdayGivesPensionikeskusUntilTheMondayAfter() {
    pensionikeskusShows("0.0028");
    kid("2026-03-19", "0.00280000");
    kid("2026-10-31", "0.00310000");

    assertThat(checker.check(TUK00, MONDAY_AFTER_A_SATURDAY_KID))
        .containsExactly(FeeCheckFinding.pass(TUK00, PENSIONIKESKUS_ONGOING_CHARGES, ALL));
    assertThat(checker.check(TUK00, TUESDAY_AFTER_A_SATURDAY_KID))
        .extracting(FeeCheckFinding::severity)
        .containsExactly(FAIL);
  }

  @Test
  void cannotRunWithoutAKidInForceAndSaysSoRatherThanPassing() {
    pensionikeskusShows("0.0028");

    assertThat(checker.check(TUK00, FRIDAY_AFTER))
        .containsExactly(
            finding(
                NOT_RUN,
                List.of(),
                "No KID in investment_ocf_publication in force on 2026-10-30 to compare"
                    + " Pensionikeskus' ongoing charges with"));
  }

  @Test
  void theSavingsFundIsNotOnPensionikeskusSoItHasNoFinding() {
    assertThat(checker.check(TKF100, FRIDAY_AFTER)).isEmpty();
  }

  private void pensionikeskusShows(String figure) {
    jdbcClient
        .sql("UPDATE fund SET ongoing_charges_figure = :figure WHERE isin = :isin")
        .param("figure", new BigDecimal(figure))
        .param("isin", TUK00.getIsin())
        .update();
  }

  private void theNextSyncFromPensionikeskusShows(String figure) {
    pensionikeskusShows(figure);
    entityManager.clear();
  }

  private void kid(String validFrom, String publishedOcf) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_publication
              (fund_code, valid_from, published_ocf, published_decimals, basis, basis_reason,
               published_in, published_by)
            VALUES ('TUK00', :validFrom, :publishedOcf, 2, 'MONTHLY_ESTIMATE',
                    'the KID in force', :publishedIn, 'portfolio-manager')
            """)
        .param("validFrom", LocalDate.parse(validFrom))
        .param("publishedOcf", new BigDecimal(publishedOcf))
        .param("publishedIn", "KID " + validFrom)
        .update();
  }

  private static FeeCheckFinding finding(
      FeeCheckSeverity severity, List<String> identifiers, String message) {
    return new FeeCheckFinding(
        TUK00, PENSIONIKESKUS_ONGOING_CHARGES, ALL, severity, message, null, identifiers, Map.of());
  }
}
