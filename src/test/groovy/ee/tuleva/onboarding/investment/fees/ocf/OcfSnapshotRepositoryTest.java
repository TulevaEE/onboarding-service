package ee.tuleva.onboarding.investment.fees.ocf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(OcfSnapshotRepository.class)
class OcfSnapshotRepositoryTest {

  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final LocalDate MARCH = LocalDate.of(2026, 3, 1);

  @Autowired private JdbcClient jdbcClient;
  @Autowired private OcfSnapshotRepository repository;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_ocf_snapshot").update();
  }

  private OcfSnapshot snapshot(LocalDate month, String totalOcf) {
    return snapshot(month, totalOcf, OcfAudit.empty());
  }

  private OcfSnapshot snapshot(LocalDate month, String totalOcf, OcfAudit audit) {
    return OcfSnapshot.computed(
        "TUK75",
        month,
        new BigDecimal(totalOcf),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal(totalOcf),
        true,
        null,
        audit);
  }

  @Test
  void saveAndFindByFundAndMonth() {
    repository.save(snapshot(APRIL, "0.00340000"));

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.fundCode()).isEqualTo("TUK75");
    assertThat(found.snapshotMonth()).isEqualTo(APRIL);
    assertThat(found.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0034"));
    assertThat(found.version()).isEqualTo(1);
    assertThat(found.publishedAt()).isNull();
  }

  @Test
  void recalculationOverwritesTheWorkingVersionInPlace() {
    repository.save(snapshot(APRIL, "0.00340000"));
    repository.save(snapshot(APRIL, "0.00500000"));

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.version()).isEqualTo(1);
    assertThat(found.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0050"));
    assertThat(repository.findAllVersions("TUK75", APRIL)).hasSize(1);
  }

  @Test
  void recalculationAfterPublishingWritesANewVersionInsteadOfOverwriting() {
    repository.save(snapshot(APRIL, "0.00340000"));
    repository.publish("TUK75", APRIL, "KID 2026");

    repository.save(snapshot(APRIL, "0.00500000"));

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
    repository.save(snapshot(APRIL, "0.00340000"));

    repository.publish("TUK75", APRIL, "KID 2026");

    var published = repository.findPublishedByFundAndMonth("TUK75", APRIL).orElseThrow();
    assertThat(published.publishedAt()).isNotNull();
    assertThat(published.publishedIn()).isEqualTo("KID 2026");
  }

  @Test
  void nothingIsPublishedUntilSomebodyPublishesIt() {
    repository.save(snapshot(APRIL, "0.00340000"));

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
            BigDecimal.ZERO,
            false,
            "{\"unresolvedIsins\":[\"XX0000000001\"]}",
            OcfAudit.empty()));

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.complete()).isFalse();
    assertThat(found.checks()).contains("XX0000000001");
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
            "[\"2026-04-29\",\"2026-04-30\"]");

    repository.save(snapshot(APRIL, "0.00340000", audit));

    var found = repository.findByFundAndMonth("TUK75", APRIL).orElseThrow();

    assertThat(found.audit()).isEqualTo(audit);
  }

  @Test
  void findLatestByFundReturnsNewest() {
    repository.save(snapshot(MARCH, "0.00300000"));
    repository.save(snapshot(APRIL, "0.00500000"));

    var latest = repository.findLatestByFund("TUK75").orElseThrow();

    assertThat(latest.snapshotMonth()).isEqualTo(APRIL);
    assertThat(latest.totalOcf()).isEqualByComparingTo(new BigDecimal("0.0050"));
  }

  @Test
  void findLatestByFundIgnoresSupersededVersions() {
    repository.save(snapshot(APRIL, "0.00340000"));
    repository.publish("TUK75", APRIL, "KID 2026");
    repository.save(snapshot(APRIL, "0.00500000"));

    var latest = repository.findLatestByFund("TUK75").orElseThrow();

    assertThat(latest.version()).isEqualTo(2);
    assertThat(latest.totalOcf()).isEqualByComparingTo(new BigDecimal("0.0050"));
  }

  @Test
  void findByFundReturnsAllDescending() {
    repository.save(snapshot(MARCH, "0.00300000"));
    repository.save(snapshot(APRIL, "0.00500000"));

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
}
