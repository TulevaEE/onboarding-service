package ee.tuleva.onboarding.investment.fees.ocf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(OcfSnapshotRepository.class)
class OcfSnapshotRepositoryTest {

  @Autowired private JdbcClient jdbcClient;
  @Autowired private OcfSnapshotRepository repository;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_ocf_snapshot").update();
  }

  @Test
  void saveAndFindByFundAndMonth() {
    var snapshot =
        new OcfSnapshot(
            null,
            "TUK75",
            LocalDate.of(2026, 4, 1),
            new BigDecimal("0.00340000"),
            new BigDecimal("0.00100000"),
            new BigDecimal("0.00070000"),
            new BigDecimal("0.00020000"),
            new BigDecimal("0.00530000"));

    repository.save(snapshot);

    var result = repository.findByFundAndMonth("TUK75", LocalDate.of(2026, 4, 1));

    assertThat(result).isPresent();
    var found = result.get();
    assertThat(found.fundCode()).isEqualTo("TUK75");
    assertThat(found.snapshotMonth()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(found.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0034"));
    assertThat(found.depotFeeRate()).isEqualByComparingTo(new BigDecimal("0.0010"));
    assertThat(found.underlyingFundCost()).isEqualByComparingTo(new BigDecimal("0.0007"));
    assertThat(found.transactionCostRate()).isEqualByComparingTo(new BigDecimal("0.0002"));
    assertThat(found.totalOcf()).isEqualByComparingTo(new BigDecimal("0.0053"));
  }

  @Test
  void saveUpsertsOnConflict() {
    var snapshot1 =
        new OcfSnapshot(
            null,
            "TUK75",
            LocalDate.of(2026, 4, 1),
            new BigDecimal("0.00340000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00340000"));

    repository.save(snapshot1);

    var snapshot2 =
        new OcfSnapshot(
            null,
            "TUK75",
            LocalDate.of(2026, 4, 1),
            new BigDecimal("0.00500000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00500000"));

    repository.save(snapshot2);

    var result = repository.findByFundAndMonth("TUK75", LocalDate.of(2026, 4, 1));
    assertThat(result).isPresent();
    assertThat(result.get().managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0050"));
  }

  @Test
  void findLatestByFundReturnsNewest() {
    repository.save(
        new OcfSnapshot(
            null,
            "TUK75",
            LocalDate.of(2026, 3, 1),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00300000")));
    repository.save(
        new OcfSnapshot(
            null,
            "TUK75",
            LocalDate.of(2026, 4, 1),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00500000")));

    var latest = repository.findLatestByFund("TUK75");

    assertThat(latest).isPresent();
    assertThat(latest.get().snapshotMonth()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(latest.get().totalOcf()).isEqualByComparingTo(new BigDecimal("0.0050"));
  }

  @Test
  void findByFundReturnsAllDescending() {
    repository.save(
        new OcfSnapshot(
            null,
            "TUK75",
            LocalDate.of(2026, 3, 1),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00300000")));
    repository.save(
        new OcfSnapshot(
            null,
            "TUK75",
            LocalDate.of(2026, 4, 1),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.00500000")));

    var results = repository.findByFund("TUK75");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).snapshotMonth()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(results.get(1).snapshotMonth()).isEqualTo(LocalDate.of(2026, 3, 1));
  }

  @Test
  void findLatestTotalOcfByFundReturnsZeroWhenEmpty() {
    var result = repository.findLatestTotalOcfByFund("TUK75");
    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void findByFundAndMonthReturnsEmptyWhenNotFound() {
    var result = repository.findByFundAndMonth("TUK75", LocalDate.of(2026, 4, 1));
    assertThat(result).isEmpty();
  }
}
