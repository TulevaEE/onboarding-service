package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.STANDARD_AFTER_AGREEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.fees.rate.ApplicableRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(OcfSnapshotRepository.class)
class OcfSnapshotRepositoryTest {

  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final LocalDate MARCH = LocalDate.of(2026, 3, 1);
  private static final LocalDate MAY_1 = LocalDate.of(2026, 5, 1);
  private static final String SYNTHETIC_ISIN = "ZZ0000000001";

  @Autowired private JdbcClient jdbcClient;
  @Autowired private OcfSnapshotRepository repository;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_ocf_snapshot").update();
  }

  private static final OcfAudit NO_AUDIT =
      new OcfAudit(
          null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

  private OcfSnapshot snapshot(LocalDate month, String totalOcf) {
    return snapshot(month, totalOcf, NO_AUDIT);
  }

  private OcfSnapshot snapshot(LocalDate month, String totalOcf, OcfAudit audit) {
    return OcfSnapshot.computed(
        "TUK75",
        month,
        new BigDecimal(totalOcf),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        RebateBasis.NET,
        BigDecimal.ZERO,
        new BigDecimal(totalOcf),
        true,
        null,
        audit);
  }

  private OcfSnapshot incompleteSnapshot(LocalDate month) {
    return OcfSnapshot.computed(
        "TUK75",
        month,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        RebateBasis.NET,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        false,
        "{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}",
        NO_AUDIT);
  }

  @Test
  void saveAndFindByFundAndMonth() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.fundCode()).isEqualTo("TUK75");
    assertThat(found.snapshotMonth()).isEqualTo(APRIL);
    assertThat(found.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0034"));
    assertThat(found.version()).isEqualTo(1);
    assertThat(found.publishedAt()).isNull();
  }

  @Test
  void recalculationOverwritesTheWorkingVersionInPlace() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());
    repository.save(snapshot(APRIL, "0.00500000"), List.of());

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.version()).isEqualTo(1);
    assertThat(found.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0050"));
    assertThat(repository.findAllVersions("TUK75", APRIL)).hasSize(1);
  }

  @Test
  void recalculationAfterPublishingWritesANewVersionInsteadOfOverwriting() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());
    publishTheWorkingVersion(APRIL, "KID 2026");

    repository.save(snapshot(APRIL, "0.00500000"), List.of());

    assertThat(repository.findAllVersions("TUK75", APRIL)).hasSize(2);

    var working = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();
    assertThat(working.version()).isEqualTo(2);
    assertThat(working.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0050"));
    assertThat(working.publishedAt()).isNull();

    var published = repository.findPublishedByFundAndMonth("TUK75", APRIL).orElseThrow();
    assertThat(published.version()).isEqualTo(1);
    assertThat(published.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0034"));
  }

  @Test
  void publishingRecordsWhereTheNumberWent() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());

    publishTheWorkingVersion(APRIL, "KID 2026");

    var published = repository.findPublishedByFundAndMonth("TUK75", APRIL).orElseThrow();
    assertThat(published.publishedAt()).isNotNull();
    assertThat(published.publishedIn()).isEqualTo("KID 2026");
  }

  @Test
  void nothingIsPublishedUntilSomebodyPublishesIt() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());

    assertThat(repository.findPublishedByFundAndMonth("TUK75", APRIL)).isEmpty();
  }

  @Test
  void completenessAndItsDiagnosticsSurviveTheRoundTrip() {
    repository.save(
        OcfSnapshot.computed(
            "TUK75",
            APRIL,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            RebateBasis.NET,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            "{\"unresolvedIsins\":[\"XX0000000001\"]}",
            NO_AUDIT),
        List.of());

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.complete()).isFalse();
    assertThat(found.checks()).contains("XX0000000001");
  }

  @Test
  void bothRebateBasesAndTheChosenOneSurviveTheRoundTrip() {
    repository.save(
        OcfSnapshot.computed(
            "TUK75",
            APRIL,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00200000"),
            new BigDecimal("0.00150000"),
            RebateBasis.NET,
            BigDecimal.ZERO,
            new BigDecimal("0.00150000"),
            true,
            null,
            NO_AUDIT),
        List.of());

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.underlyingFundCostGross()).isEqualByComparingTo(new BigDecimal("0.002"));
    assertThat(found.underlyingFundCostNet()).isEqualByComparingTo(new BigDecimal("0.0015"));
    assertThat(found.rebateBasis()).isEqualTo(RebateBasis.NET);
    assertThat(found.underlyingFundCost()).isEqualByComparingTo(new BigDecimal("0.0015"));
  }

  @Test
  void aRowSaysWhichMethodologyProducedIt() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.methodology()).isEqualTo(OcfMethodology.EX_ANTE_NET_ASSETS_V2);
  }

  @Test
  void theAuditTrailSurvivesTheRoundTrip() {
    var calculationId = UUID.randomUUID();
    var audit =
        new OcfAudit(
            LocalDate.of(2026, 4, 30),
            calculationId,
            new BigDecimal("100000000.00"),
            42L,
            false,
            LocalDate.of(2026, 2, 28),
            new BigDecimal("250000000.00"),
            LocalDate.of(2025, 5, 1),
            LocalDate.of(2026, 4, 30),
            new BigDecimal("1234.56"),
            new BigDecimal("98000000.00"),
            "[\"2026-04-29\",\"2026-04-30\"]",
            UnderlyingWeightBasis.MONTH_AVERAGE,
            new BigDecimal("0.940000000000"),
            new BigDecimal("0.970000000000"));

    repository.save(snapshot(APRIL, "0.00340000", audit), List.of());

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.audit()).isEqualTo(audit);
  }

  @Test
  void findLatestByFundReturnsNewest() {
    repository.save(snapshot(MARCH, "0.00300000"), List.of());
    repository.save(snapshot(APRIL, "0.00500000"), List.of());

    var latest = repository.findLatestByFund("TUK75").orElseThrow();

    assertThat(latest.snapshotMonth()).isEqualTo(APRIL);
    assertThat(latest.totalOcf()).isEqualByComparingTo(new BigDecimal("0.0050"));
  }

  @Test
  void findLatestByFundIgnoresSupersededVersions() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());
    publishTheWorkingVersion(APRIL, "KID 2026");
    repository.save(snapshot(APRIL, "0.00500000"), List.of());

    var latest = repository.findLatestByFund("TUK75").orElseThrow();

    assertThat(latest.version()).isEqualTo(2);
    assertThat(latest.totalOcf()).isEqualByComparingTo(new BigDecimal("0.0050"));
  }

  @Test
  void findByFundReturnsAllDescending() {
    repository.save(snapshot(MARCH, "0.00300000"), List.of());
    repository.save(snapshot(APRIL, "0.00500000"), List.of());

    var results = repository.findByFund("TUK75");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).snapshotMonth()).isEqualTo(APRIL);
    assertThat(results.get(1).snapshotMonth()).isEqualTo(MARCH);
  }

  @Test
  void findLatestTotalOcfByFundReturnsZeroWhenEmpty() {
    assertThat(repository.findLatestTotalOcfByFund("TUK75")).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void findByFundAndMonthReturnsEmptyWhenNotFound() {
    assertThat(repository.findByFundAndMonth("TUK75", APRIL)).isEmpty();
  }

  @Test
  void aPublicationRecordsTheFigureItsDateItsFeeAndWhoPublishedIt() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());
    var working = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    var publication =
        new OcfPublication(
            MAY_1,
            new BigDecimal("0.00330000"),
            new BigDecimal("0.00330000"),
            7L,
            "KID 01.05.2026",
            "portfolio-manager",
            null,
            null);

    var recorded = repository.recordPublication(working, publication);

    assertThat(recorded).isTrue();
    assertThat(repository.findPublishedInForceOn("TUK75", MAY_1))
        .contains(new KidFigure(APRIL, 1, MAY_1, new BigDecimal("0.00330000"), "KID 01.05.2026"));
    assertThat(storedPublication()).isEqualTo(publication);
  }

  @Test
  void aVersionAlreadyPublishedIsNotPublishedAgain() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());
    assertThat(publishTheWorkingVersion(APRIL, "KID 2026")).isTrue();
    var published = repository.findVersion("TUK75", APRIL, 1).orElseThrow();

    assertThat(repository.recordPublication(published, publication("KID 2026 second edition")))
        .isFalse();
  }

  @Test
  void aCalculationThatChangedAfterItWasReadIsNotPublished() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());
    var read = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();
    repository.save(snapshot(APRIL, "0.00350000"), List.of());

    assertThat(repository.recordPublication(read, publication("KID 2026"))).isFalse();
    assertThat(repository.findPublishedInForceOn("TUK75", MAY_1)).isEmpty();
  }

  @Test
  void aCalculationWhoseManagementFeeChangedUnderTheSameTotalIsNotPublished() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());
    var read = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();
    repository.save(
        OcfSnapshot.computed(
            "TUK75",
            APRIL,
            new BigDecimal("0.00300000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00040000"),
            RebateBasis.NET,
            BigDecimal.ZERO,
            new BigDecimal("0.00340000"),
            true,
            null,
            NO_AUDIT),
        List.of());

    assertThat(repository.recordPublication(read, publication("KID 2026"))).isFalse();
    assertThat(repository.findPublishedInForceOn("TUK75", MAY_1)).isEmpty();
  }

  @Test
  void anExceptionIsNotRecordedAgainstGapsThatChangedAfterTheyWereRead() {
    repository.save(incompleteSnapshot(APRIL), List.of());
    var read = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();
    repository.save(
        OcfSnapshot.computed(
            "TUK75",
            APRIL,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            RebateBasis.NET,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            "{\"gaps\":[\"NO_PUBLISHED_NAV_CALCULATION\"]}",
            NO_AUDIT),
        List.of());

    assertThat(
            repository.recordPublication(
                read,
                new OcfPublication(
                    MAY_1,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    7L,
                    "KID 2026",
                    "portfolio-manager",
                    "fund-manager",
                    "the gaps were reviewed")))
        .isFalse();
  }

  @Test
  void anIncompleteCalculationPublishedAsAnExceptionKeepsItsGapsWithTheReasonAndApprover() {
    repository.save(incompleteSnapshot(APRIL), List.of());
    var working = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    var publication =
        new OcfPublication(
            MAY_1,
            working.totalOcf(),
            new BigDecimal("0.00340000"),
            7L,
            "KID 2026",
            "portfolio-manager",
            "fund-manager",
            "the fee rate is set by the supervisory board decision");

    repository.recordPublication(working, publication);

    var published = repository.findVersion("TUK75", APRIL, 1).orElseThrow();
    assertThat(published.complete()).isFalse();
    assertThat(published.checks()).isEqualTo(working.checks());
    assertThat(storedPublication()).isEqualTo(publication);
  }

  @Test
  void theKidInForceOnADayIsTheLatestPublicationValidByThen() {
    repository.save(snapshot(MARCH, "0.00300000"), List.of());
    repository.save(snapshot(APRIL, "0.00340000"), List.of());
    repository.recordPublication(
        repository.findByFundAndMonth("TUK75", MARCH).orElseThrow(),
        publication(MARCH.plusMonths(1), "KID 01.04.2026"));
    repository.recordPublication(
        repository.findByFundAndMonth("TUK75", APRIL).orElseThrow(), publication("KID 01.05.2026"));

    assertThat(repository.findPublishedInForceOn("TUK75", MARCH.plusMonths(1).minusDays(1)))
        .isEmpty();
    assertThat(repository.findPublishedInForceOn("TUK75", MAY_1.minusDays(1)))
        .contains(
            new KidFigure(
                MARCH, 1, MARCH.plusMonths(1), new BigDecimal("0.00340000"), "KID 01.04.2026"));
    assertThat(repository.findPublishedInForceOn("TUK75", MAY_1))
        .contains(new KidFigure(APRIL, 1, MAY_1, new BigDecimal("0.00340000"), "KID 01.05.2026"));
    assertThat(repository.findPublishedInForceOn("TUV100", MAY_1)).isEmpty();
  }

  private boolean publishTheWorkingVersion(LocalDate month, String publishedIn) {
    return repository.recordPublication(
        repository.findByFundAndMonth("TUK75", month).orElseThrow(), publication(publishedIn));
  }

  private static OcfPublication publication(String publishedIn) {
    return publication(MAY_1, publishedIn);
  }

  private static OcfPublication publication(LocalDate validFrom, String publishedIn) {
    return new OcfPublication(
        validFrom,
        new BigDecimal("0.00340000"),
        new BigDecimal("0.00340000"),
        7L,
        publishedIn,
        "portfolio-manager",
        null,
        null);
  }

  private OcfPublication storedPublication() {
    return jdbcClient
        .sql(
            """
            SELECT published_valid_from, published_ocf, published_management_fee_rate,
                   published_management_fee_rate_id, published_in, published_by,
                   publication_approved_by, publication_exception_reason
            FROM investment_ocf_snapshot WHERE published_at IS NOT NULL
            """)
        .query(
            (rs, rowNum) ->
                new OcfPublication(
                    rs.getObject("published_valid_from", LocalDate.class),
                    rs.getBigDecimal("published_ocf"),
                    rs.getBigDecimal("published_management_fee_rate"),
                    rs.getLong("published_management_fee_rate_id"),
                    rs.getString("published_in"),
                    rs.getString("published_by"),
                    rs.getString("publication_approved_by"),
                    rs.getString("publication_exception_reason")))
        .single();
  }

  @Test
  void aRowCannotNameAPublicationWithoutSayingWhenItWentOut() {
    repository.save(snapshot(APRIL, "0.00340000"), List.of());

    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql("UPDATE investment_ocf_snapshot SET published_in = 'KID 2026'")
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // The previous release's MERGE sets neither version nor complete. If the code is rolled back
  // while the schema stays, its inserts have to keep working — which is what the column defaults
  // are for.
  @Test
  void anInsertFromBeforeThisMigrationStillLands() {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_snapshot
              (fund_code, snapshot_month, management_fee_rate, depot_fee_rate,
               underlying_fund_cost, transaction_cost_rate, total_ocf)
            VALUES ('TUK75', :month, 0.0034, 0, 0, 0, 0.0034)
            """)
        .param("month", APRIL)
        .update();

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.version()).isEqualTo(1);
    assertThat(found.complete()).isFalse();
  }

  @Test
  void storesEachHoldingOfTheWorkingVersionWithTheRateItWasWeighedWith() {
    var rate = givenAStoredRate(SYNTHETIC_ISIN, "0.00200000", "0.00150000");

    repository.save(
        snapshot(APRIL, "0.0050"), List.of(new OcfHolding(new BigDecimal("0.6"), rate)));

    assertThat(storedHoldings())
        .containsExactly(
            new StoredHolding(
                SYNTHETIC_ISIN,
                "0.600000000000",
                "0.00200000",
                "0.00150000",
                "AGREEMENT",
                rate.rateId(),
                rate.agreementId()));
  }

  @Test
  void theStandardRateAfterAnAgreementIsStoredAgainstThatAgreementWithoutARateRow() {
    var rate = givenAStoredRate(SYNTHETIC_ISIN, "0.00200000", "0.00150000");
    var standardRate =
        new ApplicableRate(
            SYNTHETIC_ISIN,
            rate.agreementId(),
            null,
            new BigDecimal("0.00200000"),
            new BigDecimal("0.00200000"),
            STANDARD_AFTER_AGREEMENT,
            ("its agreement ended on 2026-04-15, so the published OCF applies after it; enter the new"
                + " agreement if there is one"));

    repository.save(
        snapshot(APRIL, "0.0050"),
        List.of(
            new OcfHolding(new BigDecimal("0.3"), rate),
            new OcfHolding(new BigDecimal("0.2"), standardRate)));

    assertThat(storedHoldings())
        .containsExactlyInAnyOrder(
            new StoredHolding(
                SYNTHETIC_ISIN,
                "0.300000000000",
                "0.00200000",
                "0.00150000",
                "AGREEMENT",
                rate.rateId(),
                rate.agreementId()),
            new StoredHolding(
                SYNTHETIC_ISIN,
                "0.200000000000",
                "0.00200000",
                "0.00200000",
                "STANDARD_AFTER_AGREEMENT",
                null,
                rate.agreementId()));
  }

  @Test
  void aRecalculationReplacesTheWorkingVersionsHoldingsRatherThanAddingToThem() {
    var rate = givenAStoredRate(SYNTHETIC_ISIN, "0.00200000", "0.00150000");
    repository.save(
        snapshot(APRIL, "0.0050"), List.of(new OcfHolding(new BigDecimal("0.6"), rate)));

    repository.save(
        snapshot(APRIL, "0.0051"), List.of(new OcfHolding(new BigDecimal("0.7"), rate)));

    assertThat(storedHoldings())
        .extracting(StoredHolding::weight)
        .containsExactly("0.700000000000");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void aRecalculationWhoseHoldingsCannotBeStoredLeavesTheWorkingVersionAsItWas() {
    var rate = givenAStoredRate(SYNTHETIC_ISIN, "0.00200000", "0.00150000");
    try {
      repository.save(
          snapshot(APRIL, "0.0050"), List.of(new OcfHolding(new BigDecimal("0.6"), rate)));
      var rateNeverStored =
          new ApplicableRate(
              rate.isin(),
              rate.agreementId(),
              rate.rateId() + 1,
              rate.publishedOcf(),
              rate.netOcf(),
              rate.basis(),
              rate.reason());

      assertThatThrownBy(
              () ->
                  repository.save(
                      snapshot(APRIL, "0.0051"),
                      List.of(new OcfHolding(new BigDecimal("0.7"), rateNeverStored))))
          .isInstanceOf(DataIntegrityViolationException.class);

      assertThat(repository.findByFundAndMonth("TUK75", APRIL).orElseThrow().totalOcf())
          .isEqualByComparingTo(new BigDecimal("0.0050"));
      assertThat(storedHoldings())
          .extracting(StoredHolding::weight)
          .containsExactly("0.600000000000");
    } finally {
      deleteWhatThisTestCommitted(SYNTHETIC_ISIN);
    }
  }

  private void deleteWhatThisTestCommitted(String isin) {
    jdbcClient.sql("DELETE FROM investment_ocf_snapshot_detail").update();
    jdbcClient.sql("DELETE FROM investment_ocf_snapshot").update();
    jdbcClient
        .sql("DELETE FROM investment_instrument_fee_rate WHERE isin = :isin")
        .param("isin", isin)
        .update();
    jdbcClient
        .sql("DELETE FROM investment_instrument_fee WHERE isin = :isin")
        .param("isin", isin)
        .update();
    jdbcClient
        .sql("DELETE FROM instrument_reference WHERE isin = :isin")
        .param("isin", isin)
        .update();
  }

  private ApplicableRate givenAStoredRate(String isin, String publishedOcf, String netOcf) {
    jdbcClient
        .sql(
            """
            INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class)
            VALUES (:isin, :isin, 'FUND', 'equity')
            """)
        .param("isin", isin)
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, published_ocf, rebate_kind, rebate_terms, valid_from)
            VALUES (:isin, :publishedOcf, 'FIXED', '{}', DATE '2026-01-01')
            """)
        .param("isin", isin)
        .param("publishedOcf", new BigDecimal(publishedOcf))
        .update();
    var agreementId =
        jdbcClient
            .sql("SELECT id FROM investment_instrument_fee WHERE isin = :isin")
            .param("isin", isin)
            .query(Long.class)
            .single();
    var published = new BigDecimal(publishedOcf);
    var net = new BigDecimal(netOcf);
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee_rate
              (isin, period_start, period_end, published_ocf, rebate_rate, invoiced_fee_rate,
               net_ocf, rate_basis, rebate_kind, instrument_fee_id)
            VALUES (:isin, DATE '2026-04-01', DATE '2026-04-30', :published, :rebate, 0, :net,
                    'AGREEMENT', 'FIXED', :agreementId)
            """)
        .param("isin", isin)
        .param("published", published)
        .param("rebate", published.subtract(net))
        .param("net", net)
        .param("agreementId", agreementId)
        .update();
    var rateId =
        jdbcClient
            .sql("SELECT id FROM investment_instrument_fee_rate WHERE isin = :isin")
            .param("isin", isin)
            .query(Long.class)
            .single();
    return new ApplicableRate(isin, agreementId, rateId, published, net, AGREEMENT, null);
  }

  private List<StoredHolding> storedHoldings() {
    return jdbcClient
        .sql(
            """
            SELECT isin, weight, published_ocf, net_ocf, rate_basis, instrument_fee_rate_id,
                   instrument_fee_id
            FROM investment_ocf_snapshot_detail ORDER BY isin
            """)
        .query(
            (rs, rowNum) ->
                new StoredHolding(
                    rs.getString("isin"),
                    rs.getBigDecimal("weight").toPlainString(),
                    rs.getBigDecimal("published_ocf").toPlainString(),
                    rs.getBigDecimal("net_ocf").toPlainString(),
                    rs.getString("rate_basis"),
                    rs.getObject("instrument_fee_rate_id", Long.class),
                    rs.getObject("instrument_fee_id", Long.class)))
        .list();
  }

  private record StoredHolding(
      String isin,
      String weight,
      String publishedOcf,
      String netOcf,
      String rateBasis,
      @Nullable Long instrumentFeeRateId,
      Long instrumentFeeId) {}
}
