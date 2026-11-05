package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.EX_POST;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.MONTHLY_ESTIMATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({OcfPublicationRepository.class, OcfSnapshotRepository.class})
class OcfPublicationRepositoryTest {

  private static final LocalDate MARCH = LocalDate.of(2026, 3, 1);
  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final LocalDate APRIL_1 = APRIL;
  private static final LocalDate MAY_1 = LocalDate.of(2026, 5, 1);
  private static final OcfAudit NO_AUDIT =
      new OcfAudit(
          null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

  @Autowired private JdbcClient jdbcClient;
  @Autowired private OcfPublicationRepository repository;
  @Autowired private OcfSnapshotRepository snapshotRepository;

  @Test
  void aPublicationFromACalculationRecordsItsFigureBasisFeeAndPublisherAndMarksTheVersion() {
    var working = storedCalculation(APRIL, "0.00340000", "0.00340000", true, null);
    var publication = fromCalculation(working, MAY_1, "KID 01.05.2026");

    var recorded = repository.recordFromCalculation(working, publication);

    assertThat(recorded).isTrue();
    assertThat(storedPublications()).containsExactly(publication);
    var published = snapshotRepository.findVersion("TUK75", APRIL, 1).orElseThrow();
    assertThat(published.publishedAt()).isNotNull();
    assertThat(published.publishedIn()).isEqualTo("KID 01.05.2026");
    assertThat(repository.findInForceOn("TUK75", MAY_1))
        .contains(
            new KidFigure(
                onlyPublicationId(),
                MAY_1,
                new BigDecimal("0.00330000"),
                2,
                MONTHLY_ESTIMATE,
                "KID 01.05.2026"));
  }

  @Test
  void aVersionAlreadyPublishedIsNotPublishedAgain() {
    var working = storedCalculation(APRIL, "0.00340000", "0.00340000", true, null);
    repository.recordFromCalculation(working, fromCalculation(working, MAY_1, "KID 01.05.2026"));
    var published = snapshotRepository.findVersion("TUK75", APRIL, 1).orElseThrow();

    assertThat(
            repository.recordFromCalculation(
                published, fromCalculation(published, MAY_1, "KID 01.05.2026 second edition")))
        .isFalse();
    assertThat(storedPublications()).hasSize(1);
  }

  @Test
  void aCalculationWhoseTotalChangedAfterItWasReadIsNotPublished() {
    var read = storedCalculation(APRIL, "0.00340000", "0.00340000", true, null);
    storedCalculation(APRIL, "0.00350000", "0.00350000", true, null);

    assertThat(repository.recordFromCalculation(read, fromCalculation(read, MAY_1, "KID 2026")))
        .isFalse();
    assertThat(storedPublications()).isEmpty();
  }

  @Test
  void aCalculationWhoseManagementFeeChangedUnderTheSameTotalIsNotPublished() {
    var read = storedCalculation(APRIL, "0.00340000", "0.00340000", true, null);
    storedCalculation(APRIL, "0.00300000", "0.00340000", true, null);

    assertThat(repository.recordFromCalculation(read, fromCalculation(read, MAY_1, "KID 2026")))
        .isFalse();
    assertThat(storedPublications()).isEmpty();
  }

  @Test
  void anIncompleteCalculationWhoseGapsChangedAfterTheyWereReadIsNotPublished() {
    var read =
        storedCalculation(APRIL, "0", "0", false, "{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}");
    storedCalculation(APRIL, "0", "0", false, "{\"gaps\":[\"NO_PUBLISHED_NAV_CALCULATION\"]}");

    assertThat(repository.recordFromCalculation(read, fromCalculation(read, MAY_1, "KID 2026")))
        .isFalse();
    assertThat(storedPublications()).isEmpty();
  }

  @Test
  void aGivenExPostFigureIsRecordedWithItsPeriodAndWithoutACalculation() {
    var given =
        OcfPublication.notFromACalculation(
            "TUK75",
            MAY_1,
            new BigDecimal("0.00463800"),
            2,
            EX_POST,
            "ex-post 2025 differs from the estimate at the published precision",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31),
            "KID 01.05.2026",
            "portfolio-manager");

    var recorded = repository.recordGiven(given);

    assertThat(recorded)
        .isEqualTo(
            new KidFigure(
                onlyPublicationId(),
                MAY_1,
                new BigDecimal("0.00463800"),
                2,
                EX_POST,
                "KID 01.05.2026"));
    assertThat(storedPublications()).containsExactly(given);
  }

  @Test
  void theKidInForceOnADayIsTheLatestPublicationValidByThen() {
    var april = repository.recordGiven(given("TUK75", APRIL_1, "0.00460000", "KID 01.04.2026"));
    var may = repository.recordGiven(given("TUK75", MAY_1, "0.00450000", "KID 01.05.2026"));
    repository.recordGiven(given("TUV100", MAY_1, "0.00400000", "KID 01.05.2026"));

    assertThat(repository.findInForceOn("TUK75", APRIL_1.minusDays(1))).isEmpty();
    assertThat(repository.findInForceOn("TUK75", MAY_1.minusDays(1))).contains(april);
    assertThat(repository.findInForceOn("TUK75", MAY_1)).contains(may);
    assertThat(repository.findInForceOn("TKF100", MAY_1)).isEmpty();
  }

  @Test
  void twoKidsValidFromTheSameDayResolveToTheOneRecordedLast() {
    repository.recordGiven(given("TUK75", MAY_1, "0.00460000", "KID 01.05.2026"));
    var corrected =
        repository.recordGiven(given("TUK75", MAY_1, "0.00450000", "KID 01.05.2026 corrected"));

    assertThat(repository.findInForceOn("TUK75", MAY_1)).contains(corrected);
  }

  @Test
  void aKidIsRecordedOnceForItsFundDateAndDocument() {
    repository.recordGiven(given("TUK75", MAY_1, "0.00460000", "KID 01.05.2026"));

    assertThat(repository.isRecorded("TUK75", MAY_1, "KID 01.05.2026")).isTrue();
    assertThat(repository.isRecorded("TUK75", MAY_1, "KID 01.05.2026 corrected")).isFalse();
    assertThat(repository.isRecorded("TUK75", MARCH, "KID 01.05.2026")).isFalse();
    assertThat(repository.isRecorded("TUK00", MAY_1, "KID 01.05.2026")).isFalse();
    assertThatThrownBy(
            () -> repository.recordGiven(given("TUK75", MAY_1, "0.00450000", "KID 01.05.2026")))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void anExPostFigureNamesItsPeriod() {
    assertThatThrownBy(() -> insertRaw("EX_POST", 2, null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void aKidRecordedMeanwhileFailsThePublicationAndLeavesTheCalculationUnpublished() {
    var working = storedCalculation(APRIL, "0.00340000", "0.00340000", true, null);
    try {
      var recordedMeanwhile =
          repository.recordGiven(given("TUK75", MAY_1, "0.00460000", "KID 01.05.2026"));
      try {
        assertThatThrownBy(
                () ->
                    repository.recordFromCalculation(
                        working, fromCalculation(working, MAY_1, "KID 01.05.2026")))
            .isInstanceOf(DuplicateKeyException.class);
        assertThat(snapshotRepository.findVersion("TUK75", APRIL, 1).orElseThrow().publishedAt())
            .isNull();
      } finally {
        jdbcClient
            .sql("DELETE FROM investment_ocf_publication WHERE id = :id")
            .param("id", recordedMeanwhile.publicationId())
            .update();
      }
    } finally {
      jdbcClient
          .sql("DELETE FROM investment_ocf_publication WHERE snapshot_id = :snapshotId")
          .param("snapshotId", working.storedId())
          .update();
      jdbcClient
          .sql("DELETE FROM investment_ocf_snapshot WHERE id = :snapshotId")
          .param("snapshotId", working.storedId())
          .update();
    }
  }

  @Test
  void anExPostPeriodEndsBeforeTheKidTakesEffect() {
    assertThatThrownBy(() -> insertRaw("EX_POST", 2, null, null, LocalDate.of(2025, 5, 1), MAY_1))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anExPostPeriodHasBothEnds() {
    assertThatThrownBy(() -> insertRaw("EX_POST", 2, null, null, LocalDate.of(2025, 1, 1), null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anExPostPeriodEndsAfterItStarts() {
    assertThatThrownBy(
            () ->
                insertRaw(
                    "EX_POST", 2, null, null, LocalDate.of(2025, 12, 31), LocalDate.of(2025, 1, 1)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void anEstimateDoesNotNameAnExPostPeriod() {
    assertThatThrownBy(
            () ->
                insertRaw(
                    "MONTHLY_ESTIMATE",
                    2,
                    null,
                    null,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void anExPostFigureDoesNotComeFromACalculation() {
    var calculation = storedCalculation(APRIL, "0.00340000", "0.00340000", true, null);

    assertThatThrownBy(
            () ->
                insertRaw(
                    "EX_POST",
                    2,
                    calculation.storedId(),
                    new BigDecimal("0.00330000"),
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aFigureFromACalculationCarriesTheFeeRateInForceOnItsDate() {
    var calculation = storedCalculation(APRIL, "0.00340000", "0.00340000", true, null);

    assertThatThrownBy(
            () -> insertRaw("MONTHLY_ESTIMATE", 2, calculation.storedId(), null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aGivenFigureCarriesNoFeeRateOfItsOwn() {
    assertThatThrownBy(
            () -> insertRaw("MONTHLY_ESTIMATE", 2, null, new BigDecimal("0.00330000"), null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aKidPrintsItsFigureWithAtLeastOneDecimal() {
    assertThatThrownBy(() -> insertRaw("MONTHLY_ESTIMATE", 0, null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aKidPrintsItsFigureWithAtMostFourDecimals() {
    assertThatThrownBy(() -> insertRaw("MONTHLY_ESTIMATE", 5, null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theDecimalsAKidPrintedWithSurviveTheRoundTrip() {
    var oneDecimal = repository.recordGiven(given("TKF100", MAY_1, "0.00520000", "KID 01.05.2026"));

    assertThat(repository.findInForceOn("TKF100", MAY_1)).contains(oneDecimal);
    assertThat(oneDecimal.publishedDecimals()).isEqualTo(1);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void theBasisIsAnEstimateOrExPost() {
    assertThatThrownBy(() -> insertRaw("GUESS", 2, null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertRaw(
      String basis,
      int publishedDecimals,
      @Nullable Long snapshotId,
      @Nullable BigDecimal managementFeeRate,
      @Nullable LocalDate periodStart,
      @Nullable LocalDate periodEnd) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_publication
              (fund_code, valid_from, published_ocf, published_decimals, basis, basis_reason,
               snapshot_id,
               management_fee_rate, management_fee_rate_id, ex_post_period_start,
               ex_post_period_end, published_in, published_by)
            VALUES ('TUK75', :validFrom, 0.0046, :publishedDecimals, :basis, 'a reason',
                    :snapshotId,
                    :managementFeeRate, :managementFeeRateId, :periodStart, :periodEnd,
                    'KID 2026', 'portfolio-manager')
            """)
        .param("validFrom", MAY_1)
        .param("publishedDecimals", publishedDecimals)
        .param("basis", basis)
        .param("snapshotId", snapshotId)
        .param("managementFeeRate", managementFeeRate)
        .param("managementFeeRateId", managementFeeRate == null ? null : 7L)
        .param("periodStart", periodStart)
        .param("periodEnd", periodEnd)
        .update();
  }

  private OcfSnapshot storedCalculation(
      LocalDate month,
      String managementFeeRate,
      String totalOcf,
      boolean complete,
      @Nullable String checks) {
    snapshotRepository.save(
        OcfSnapshot.computed(
            "TUK75",
            month,
            new BigDecimal(managementFeeRate),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal(totalOcf).subtract(new BigDecimal(managementFeeRate)),
            RebateBasis.NET,
            BigDecimal.ZERO,
            new BigDecimal(totalOcf),
            complete,
            checks,
            NO_AUDIT),
        List.of());
    return snapshotRepository.findByFundAndMonth("TUK75", month).orElseThrow();
  }

  private static OcfPublication fromCalculation(
      OcfSnapshot calculation, LocalDate validFrom, String publishedIn) {
    return new OcfPublication(
        "TUK75",
        validFrom,
        new BigDecimal("0.00330000"),
        2,
        MONTHLY_ESTIMATE,
        "the monthly estimate",
        calculation.storedId(),
        new BigDecimal("0.00330000"),
        7L,
        null,
        null,
        publishedIn,
        "portfolio-manager");
  }

  private static OcfPublication given(
      String fundCode, LocalDate validFrom, String publishedOcf, String publishedIn) {
    return OcfPublication.notFromACalculation(
        fundCode,
        validFrom,
        new BigDecimal(publishedOcf),
        PublishedPrecision.of(TulevaFund.fromCode(fundCode)).decimals(),
        MONTHLY_ESTIMATE,
        "the KID already in force",
        null,
        null,
        publishedIn,
        "portfolio-manager");
  }

  private long onlyPublicationId() {
    return jdbcClient.sql("SELECT id FROM investment_ocf_publication").query(Long.class).single();
  }

  private List<OcfPublication> storedPublications() {
    return jdbcClient
        .sql("SELECT * FROM investment_ocf_publication ORDER BY id")
        .query(
            (rs, rowNum) ->
                new OcfPublication(
                    rs.getString("fund_code"),
                    rs.getObject("valid_from", LocalDate.class),
                    rs.getBigDecimal("published_ocf"),
                    rs.getInt("published_decimals"),
                    OcfPublicationBasis.valueOf(rs.getString("basis")),
                    rs.getString("basis_reason"),
                    rs.getObject("snapshot_id", Long.class),
                    rs.getBigDecimal("management_fee_rate"),
                    rs.getObject("management_fee_rate_id", Long.class),
                    rs.getObject("ex_post_period_start", LocalDate.class),
                    rs.getObject("ex_post_period_end", LocalDate.class),
                    rs.getString("published_in"),
                    rs.getString("published_by")))
        .list();
  }
}
