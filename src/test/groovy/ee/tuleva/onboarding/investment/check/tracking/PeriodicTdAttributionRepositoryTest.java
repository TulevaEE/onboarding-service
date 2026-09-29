package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
class PeriodicTdAttributionRepositoryTest {

  private static final LocalDate JUNE_START = LocalDate.of(2026, 6, 1);
  private static final LocalDate JUNE_END = LocalDate.of(2026, 6, 30);

  @Autowired PeriodicTdAttributionRepository repository;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcClient jdbcClient;

  @Test
  void aStoredPeriodCanBeReplacedInOneTransaction() {
    var stored = attribution(new BigDecimal("0.00010000"));
    stored.addDetail(TdAttributionDetail.builder().isin("IE00BFG1TM61").build());
    repository.saveAndFlush(stored);
    entityManager.clear();

    repository.deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
        TUK75, JUNE_START, JUNE_END, MONTHLY);
    repository.saveAndFlush(attribution(new BigDecimal("0.00020000")));

    var rows = repository.findAll();
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTdGeometric()).isEqualByComparingTo("0.0002");
    assertThat(detailRowCount()).isZero();
  }

  private long detailRowCount() {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM investment_td_attribution_detail")
        .query(Long.class)
        .single();
  }

  private static PeriodicTdAttribution attribution(BigDecimal tdGeometric) {
    return PeriodicTdAttribution.builder()
        .fund(TUK75)
        .periodStart(JUNE_START)
        .periodEnd(JUNE_END)
        .periodType(MONTHLY)
        .fundReturn(new BigDecimal("0.01000000"))
        .modelReturn(new BigDecimal("0.00990000"))
        .tdGeometric(tdGeometric)
        .build();
  }
}
