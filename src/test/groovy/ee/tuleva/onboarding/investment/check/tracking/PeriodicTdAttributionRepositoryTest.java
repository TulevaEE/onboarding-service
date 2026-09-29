package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.QUARTERLY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
  void aStoredPeriodCanBeReplacedInOneTransactionLeavingEveryOtherPeriodAsItWas() {
    var stored = attribution(TUK75, JUNE_START, JUNE_END, MONTHLY, new BigDecimal("0.00010000"));
    stored.addDetail(TdAttributionDetail.builder().isin("IE00BFG1TM61").build());
    repository.saveAndFlush(stored);
    var otherFundsJune =
        attribution(TUK00, JUNE_START, JUNE_END, MONTHLY, new BigDecimal("0.00030000"));
    var sameFundsJuly =
        attribution(
            TUK75,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            MONTHLY,
            new BigDecimal("0.00040000"));
    var sameFundsSecondQuarter =
        attribution(
            TUK75, LocalDate.of(2026, 4, 1), JUNE_END, QUARTERLY, new BigDecimal("0.00050000"));
    repository.saveAllAndFlush(List.of(otherFundsJune, sameFundsJuly, sameFundsSecondQuarter));
    entityManager.clear();

    repository.deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
        TUK75, JUNE_START, JUNE_END, MONTHLY);
    repository.saveAndFlush(
        attribution(TUK75, JUNE_START, JUNE_END, MONTHLY, new BigDecimal("0.00020000")));

    assertThat(repository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "createdAt", "details")
        .containsExactlyInAnyOrder(
            attribution(TUK75, JUNE_START, JUNE_END, MONTHLY, new BigDecimal("0.00020000")),
            otherFundsJune,
            sameFundsJuly,
            sameFundsSecondQuarter);
    assertThat(detailRowCount()).isZero();
  }

  private long detailRowCount() {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM investment_td_attribution_detail")
        .query(Long.class)
        .single();
  }

  private static PeriodicTdAttribution attribution(
      TulevaFund fund,
      LocalDate periodStart,
      LocalDate periodEnd,
      PeriodType periodType,
      BigDecimal tdGeometric) {
    return PeriodicTdAttribution.builder()
        .fund(fund)
        .periodStart(periodStart)
        .periodEnd(periodEnd)
        .periodType(periodType)
        .fundReturn(new BigDecimal("0.01000000"))
        .modelReturn(new BigDecimal("0.00990000"))
        .tdGeometric(tdGeometric)
        .build();
  }
}
