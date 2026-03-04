package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class EpisUnitCountLedgerRecorderIntegrationTest {

  @Autowired EpisUnitCountLedgerRecorder recorder;
  @Autowired LedgerAccountService ledgerAccountService;
  @Autowired JdbcClient jdbcClient;
  @Autowired EntityManager entityManager;

  private static final LocalDate YESTERDAY = LocalDate.of(2025, 3, 14);

  @Test
  void recordUnitCount_recordsTotalUnitsAndIsIdempotent() {
    BigDecimal totalUnits = new BigDecimal("1050000.00000");

    recorder.recordUnitCount(TUK75, YESTERDAY, totalUnits);
    entityManager.flush();
    entityManager.clear();

    BigDecimal balance = getAccountBalance(FUND_UNITS_OUTSTANDING, TUK75);
    assertThat(balance).isEqualByComparingTo("1050000.00000");

    recorder.recordUnitCount(TUK75, YESTERDAY, totalUnits);
    entityManager.flush();
    entityManager.clear();

    int transactionCount =
        jdbcClient
            .sql(
                "SELECT COUNT(*) FROM ledger.transaction WHERE transaction_type = 'UNIT_COUNT_UPDATE'")
            .query(Integer.class)
            .single();
    assertThat(transactionCount).isEqualTo(1);
  }

  private BigDecimal getAccountBalance(
      SystemAccount systemAccount, ee.tuleva.onboarding.fund.TulevaFund fund) {
    return ledgerAccountService
        .findSystemAccount(systemAccount, fund)
        .map(LedgerAccount::getBalance)
        .orElse(BigDecimal.ZERO);
  }
}
