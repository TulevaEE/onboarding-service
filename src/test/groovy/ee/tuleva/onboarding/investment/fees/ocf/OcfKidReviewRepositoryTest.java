package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.MONTHLY_ESTIMATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({OcfKidReviewRepository.class, OcfPublicationRepository.class, OcfSnapshotRepository.class})
class OcfKidReviewRepositoryTest {

  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final LocalDate MARCH_1 = LocalDate.of(2026, 3, 1);
  private static final KidReview REVIEW =
      new KidReview(
          "portfolio-manager",
          "the difference comes from a one-off rebate, the KID stays",
          Instant.parse("2026-05-05T08:00:00Z"));

  @Autowired private OcfKidReviewRepository repository;
  @Autowired private OcfPublicationRepository publicationRepository;
  @Autowired private OcfSnapshotRepository snapshotRepository;
  @Autowired private JdbcClient jdbcClient;

  private OcfSnapshot calculation;
  private KidFigure kid;

  @BeforeEach
  void aCalculationDifferingFromTheKidInForce() {
    snapshotRepository.save(
        OcfSnapshot.computed(
            "TUK75",
            APRIL,
            new BigDecimal("0.00200000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00250000"),
            RebateBasis.NET,
            BigDecimal.ZERO,
            new BigDecimal("0.00450000"),
            true,
            "{\"gaps\":[]}",
            new OcfAudit(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null)),
        List.of());
    calculation = snapshotRepository.findByFundAndMonth("TUK75", APRIL).orElseThrow();
    kid = publicationRepository.recordGiven(kidOf("0.00460000", 2, "KID 01.03.2026"));
  }

  @Test
  void aReviewKeepsTheKidForAFigureThatRoundsToTheReviewedOne() {
    repository.record(calculation, kid, REVIEW);

    assertThat(repository.findLatestKeeping(kid, new BigDecimal("0.00451"))).contains(REVIEW);
  }

  @Test
  void aFigureThatRoundsAwayFromTheReviewedOneIsNotKeptByThatReview() {
    repository.record(calculation, kid, REVIEW);

    assertThat(repository.findLatestKeeping(kid, new BigDecimal("0.00444"))).isEmpty();
  }

  @Test
  void aReviewOfAKidPrintedAtOneDecimalKeepsItForAnyFigureRoundingToTheSameTenth() {
    var oneDecimalKid =
        publicationRepository.recordGiven(kidOf("0.00520000", 1, "KID 01.03.2026 one decimal"));
    repository.record(calculation, oneDecimalKid, REVIEW);

    assertThat(repository.findLatestKeeping(oneDecimalKid, new BigDecimal("0.0049")))
        .contains(REVIEW);
    assertThat(repository.findLatestKeeping(oneDecimalKid, new BigDecimal("0.0056"))).isEmpty();
  }

  @Test
  void aReviewOfAnotherKidDoesNotKeepThisOne() {
    repository.record(calculation, kid, REVIEW);
    var anotherKid = publicationRepository.recordGiven(kidOf("0.00460000", 2, "KID 01.04.2026"));

    assertThat(repository.findLatestKeeping(anotherKid, new BigDecimal("0.0045"))).isEmpty();
  }

  @Test
  void theLatestReviewKeepingTheFigureIsTheOneShown() {
    var later =
        new KidReview("portfolio-manager", "reviewed again", Instant.parse("2026-06-03T08:00:00Z"));
    repository.record(calculation, kid, REVIEW);
    repository.record(calculation, kid, later);

    assertThat(repository.findLatestKeeping(kid, new BigDecimal("0.0045"))).contains(later);
  }

  @Test
  void aReviewRecordsTheKidTheCalculationAndTheFigureItKept() {
    repository.record(calculation, kid, REVIEW);

    assertThat(
            jdbcClient
                .sql(
                    """
                    SELECT fund_code, publication_id, snapshot_id, calculation_fingerprint,
                           reviewed_ocf
                    FROM investment_ocf_kid_review
                    """)
                .query(
                    (rs, rowNum) ->
                        List.of(
                            rs.getString("fund_code"),
                            rs.getLong("publication_id"),
                            rs.getLong("snapshot_id"),
                            rs.getString("calculation_fingerprint"),
                            rs.getBigDecimal("reviewed_ocf")))
                .single())
        .isEqualTo(
            List.of(
                "TUK75",
                kid.publicationId(),
                calculation.storedId(),
                OcfCalculationFingerprint.of(calculation),
                new BigDecimal("0.00450000")));
  }

  private static OcfPublication kidOf(String publishedOcf, int decimals, String publishedIn) {
    return new OcfPublication(
        "TUK75",
        MARCH_1,
        new BigDecimal(publishedOcf),
        decimals,
        MONTHLY_ESTIMATE,
        "the KID already in force",
        null,
        null,
        null,
        null,
        publishedIn,
        "portfolio-manager");
  }
}
