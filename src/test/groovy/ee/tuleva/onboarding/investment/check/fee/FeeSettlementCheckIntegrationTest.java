package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeCalculationService;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FeeSettlementCheckIntegrationTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate LAST_DAY_OF_MAY = LocalDate.of(2026, 5, 31);
  private static final LocalDate FIRST_DAY_OF_JUNE = LocalDate.of(2026, 6, 1);
  private static final LocalDate THIRD_BUSINESS_DAY_OF_JUNE = LocalDate.of(2026, 6, 3);
  private static final BigDecimal BASE_VALUE = new BigDecimal("1000000000");
  private static final BigDecimal CORRECTION = new BigDecimal("13.32");

  @Autowired private FeeCheckService feeCheckService;
  @Autowired private FeeCalculationService feeCalculationService;
  @Autowired private NavFeeAccrualLedger navFeeAccrualLedger;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private OperationsNotificationService notificationService;

  @BeforeEach
  void setUp() {
    insertFeeRates();
    insertDepotFeeTiers();
  }

  @Test
  void aCleanlySettledMonthPasses() {
    accrueFor(LAST_DAY_OF_MAY);
    accrueFor(FIRST_DAY_OF_JUNE);

    runMonthlyChecks();

    var event = findEvent("SETTLEMENT_COMPLETENESS", "MANAGEMENT");
    assertThat(event.get("severity")).isEqualTo("PASS");
    assertThat(event.get("deviation_found")).isEqualTo(false);
    assertThat(event.get("fee_month")).isNotNull();
  }

  // The real 2026-05 incident: the correction was posted after the month had already been settled,
  // so the duplicate-reference guard dropped the re-settlement with nothing but an INFO log.
  @Test
  void aCorrectionPostedAfterSettlementLeavesAResidualInTheClosedMonth() {
    accrueFor(LAST_DAY_OF_MAY);
    accrueFor(FIRST_DAY_OF_JUNE);

    insertManagementFeeCorrection(LocalDate.of(2026, 5, 19), CORRECTION);

    runMonthlyChecks();

    var event = findEvent("SETTLEMENT_COMPLETENESS", "MANAGEMENT");
    assertThat(event.get("severity")).isEqualTo("FAIL");
    assertThat((BigDecimal) event.get("deviation_amount")).isEqualByComparingTo(CORRECTION);
  }

  @Test
  void anOrphanSettlementLeftAfterAccrualsWereDeletedIsDetected() {
    accrueFor(LAST_DAY_OF_MAY);
    accrueFor(FIRST_DAY_OF_JUNE);

    navFeeAccrualLedger.deleteFeeAccrualsFromDate(TUK75, MAY);

    runMonthlyChecks();

    assertThat(findEvent("SETTLEMENT_COMPLETENESS", "MANAGEMENT").get("severity"))
        .isEqualTo("FAIL");
  }

  @Test
  void aMonthNotYetCrossedIsNotRunRatherThanAFailure() {
    accrueFor(LAST_DAY_OF_MAY);

    runMonthlyChecks();

    var event = findEvent("SETTLEMENT_COMPLETENESS", "MANAGEMENT");
    assertThat(event.get("severity")).isEqualTo("NOT_RUN");
    assertThat(event.get("deviation_found")).isEqualTo(false);
  }

  @Test
  void monthlyEventsCarryTheFeeMonthSoEachMonthDiffsOnItsOwnHistory() {
    accrueFor(LAST_DAY_OF_MAY);
    accrueFor(FIRST_DAY_OF_JUNE);

    runMonthlyChecks();

    assertThat(findEvent("SETTLEMENT_COMPLETENESS", "MANAGEMENT").get("fee_month").toString())
        .isEqualTo(MAY.toString());
  }

  private void runMonthlyChecks() {
    feeCheckService.runMonthlyChecks(List.of(TUK75), MAY, THIRD_BUSINESS_DAY_OF_JUNE);
  }

  private void accrueFor(LocalDate date) {
    var cutoff = date.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
    feeCalculationService.calculateFeesForNav(TUK75, date, BASE_VALUE, cutoff, null);
  }

  private void insertManagementFeeCorrection(LocalDate date, BigDecimal amount) {
    var transactionId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.transaction (id, transaction_type, transaction_date, metadata)
            VALUES (:id, CAST('ADJUSTMENT' AS ledger.transaction_type), :date,
                    CAST('{"operationType":"MANAGEMENT_FEE_CORRECTION","fund":"TUK75"}' AS jsonb))
            """)
        .param("id", transactionId)
        .param("date", Timestamp.from(instantAt(date)))
        .update();
    insertEntry(transactionId, accountId("MANAGEMENT_FEE_ACCRUAL:TUK75"), amount);
    insertEntry(transactionId, accountId("NAV_EQUITY:TUK75"), amount.negate());
  }

  private void insertEntry(UUID transactionId, UUID accountId, BigDecimal amount) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.entry (id, account_id, transaction_id, amount, asset_type)
            VALUES (:id, :accountId, :transactionId, :amount, CAST('EUR' AS ledger.asset_type))
            """)
        .param("id", UUID.randomUUID())
        .param("accountId", accountId)
        .param("transactionId", transactionId)
        .param("amount", amount)
        .update();
  }

  private UUID accountId(String name) {
    return jdbcClient
        .sql("SELECT id FROM ledger.account WHERE name = :name")
        .param("name", name)
        .query(UUID.class)
        .single();
  }

  private Instant instantAt(LocalDate date) {
    return date.atTime(8, 0).atZone(ESTONIAN_ZONE).toInstant();
  }

  private Map<String, Object> findEvent(String checkType, String feeScope) {
    return jdbcClient
        .sql(
            """
            SELECT severity, deviation_found, deviation_amount, fee_month, result
            FROM investment_fee_check_event
            WHERE fund_code = :fundCode AND check_type = :checkType AND fee_scope = :feeScope
            ORDER BY created_at DESC, id DESC
            """)
        .param("fundCode", TUK75.name())
        .param("checkType", checkType)
        .param("feeScope", feeScope)
        .query()
        .listOfRows()
        .getFirst();
  }

  private void insertFeeRates() {
    for (TulevaFund fund : TulevaFund.values()) {
      jdbcClient
          .sql(
              """
              INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, created_by)
              VALUES (:fundCode, 'MANAGEMENT', 0.0025, :validFrom, 'TEST')
              """)
          .param("fundCode", fund.name())
          .param("validFrom", LocalDate.of(2025, 1, 1))
          .update();
    }
  }

  private void insertDepotFeeTiers() {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_depot_fee_tier (min_aum, annual_rate, valid_from)
            VALUES (0, 0.01, :validFrom)
            """)
        .param("validFrom", LocalDate.of(2025, 1, 1))
        .update();
  }
}
