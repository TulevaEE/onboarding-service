package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.fees.FeeBases;
import ee.tuleva.onboarding.investment.fees.FeeCalculationService;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final LocalDate LAST_DAY_OF_APRIL = LocalDate.of(2026, 4, 30);
  private static final LocalDate FIRST_DAY_OF_MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 5, 15);
  private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate LAST_DAY_OF_MAY = LocalDate.of(2026, 5, 31);
  private static final LocalDate FIRST_DAY_OF_JUNE = LocalDate.of(2026, 6, 1);
  private static final LocalDate THIRD_BUSINESS_DAY_OF_JUNE = LocalDate.of(2026, 6, 3);
  private static final BigDecimal BASE_VALUE = new BigDecimal("1000000000");
  private static final BigDecimal CORRECTION = new BigDecimal("13.32");
  private static final List<TulevaFund> PENSION_FUNDS = List.of(TUK75, TUK00, TUV100);

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

    deleteFeeAccrualLedgerTransactionsFrom(TUK75, MAY);

    runMonthlyChecks();

    assertThat(findEvent("SETTLEMENT_COMPLETENESS", "MANAGEMENT").get("severity"))
        .isEqualTo("FAIL");
  }

  private void deleteFeeAccrualLedgerTransactionsFrom(TulevaFund fund, LocalDate fromDate) {
    Instant cutoff = fromDate.atTime(9, 0).atZone(ESTONIAN_ZONE).toInstant();
    jdbcClient
        .sql(
            """
            DELETE FROM ledger.entry
            WHERE transaction_id IN (
              SELECT id FROM ledger.transaction
              WHERE transaction_type = 'FEE_ACCRUAL'
                AND CAST(metadata AS VARCHAR) LIKE :fundPattern
                AND transaction_date >= :cutoff)
            """)
        .param("fundPattern", "%\"fund\":%\"" + fund.name() + "\"%")
        .param("cutoff", Timestamp.from(cutoff))
        .update();
    jdbcClient
        .sql(
            """
            DELETE FROM ledger.transaction
            WHERE transaction_type = 'FEE_ACCRUAL'
              AND CAST(metadata AS VARCHAR) LIKE :fundPattern
              AND transaction_date >= :cutoff
            """)
        .param("fundPattern", "%\"fund\":%\"" + fund.name() + "\"%")
        .param("cutoff", Timestamp.from(cutoff))
        .update();
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

  @Test
  void everyPensionFundsPaymentMatchingWhatWasSettledPasses() {
    PENSION_FUNDS.forEach(
        fund -> {
          settleAprilFor(fund);
          insertManagementFeePayment(fund, PAYMENT_DATE, settledFor(fund));
        });

    feeCheckService.runMonthlyChecks(PENSION_FUNDS, MAY, APRIL, THIRD_BUSINESS_DAY_OF_JUNE);

    assertThat(PENSION_FUNDS)
        .extracting(fund -> severityOf(fund, "CASH_SETTLEMENT_OBSERVED", "MANAGEMENT"))
        .containsOnly("PASS");
  }

  @Test
  void aPensionFundsPaymentWithNoSettlementBehindItIsReported() {
    insertSystemAccount("MANAGEMENT_FEE:TUV100", "EXPENSE");
    insertManagementFeePayment(TUV100, PAYMENT_DATE, new BigDecimal("100.00"));

    runCashChecksFor(TUV100);

    assertThat(severityOf(TUV100, "CASH_SETTLEMENT_OBSERVED", "MANAGEMENT")).isEqualTo("WARNING");
  }

  @Test
  void aPaymentFiveCentsAwayFromWhatWasSettledStillPasses() {
    settleAprilFor(TUK75);
    insertManagementFeePayment(TUK75, PAYMENT_DATE, settledFor(TUK75).add(new BigDecimal("0.05")));

    runCashChecksFor(TUK75);

    assertThat(severityOf(TUK75, "CASH_SETTLEMENT_OBSERVED", "MANAGEMENT")).isEqualTo("PASS");
  }

  @Test
  void aPaymentSixCentsAwayFromWhatWasSettledIsReported() {
    settleAprilFor(TUK75);
    insertManagementFeePayment(TUK75, PAYMENT_DATE, settledFor(TUK75).add(new BigDecimal("0.06")));

    runCashChecksFor(TUK75);

    assertThat(severityOf(TUK75, "CASH_SETTLEMENT_OBSERVED", "MANAGEMENT")).isEqualTo("WARNING");
  }

  // A deviation is announced once. The second run, with identical state, must produce no transition
  // rather than repeat itself for as long as the deviation goes unfixed.
  @Test
  void aRepeatedRunWithTheSameStateSendsNothingASecondTime() {
    accrueFor(LAST_DAY_OF_MAY);
    accrueFor(FIRST_DAY_OF_JUNE);
    insertManagementFeeCorrection(LocalDate.of(2026, 5, 19), CORRECTION);

    runMonthlyChecks();
    runMonthlyChecks();

    verify(notificationService, times(1)).sendMessage(any(), any());
  }

  @Test
  void aCleanlySettledMonthSendsNothingAtAll() {
    accrueFor(LAST_DAY_OF_MAY);
    accrueFor(FIRST_DAY_OF_JUNE);

    runMonthlyChecks();

    verifyNoInteractions(notificationService);
  }

  @Test
  void aPaymentMatchingWhatWasSettledPasses() {
    settleAprilFor(TKF100);
    insertManagementFeePayment(TKF100, PAYMENT_DATE, settledFor(TKF100));

    runCashChecksFor(TKF100);

    assertThat(severityOf(TKF100, "CASH_SETTLEMENT_OBSERVED", "MANAGEMENT")).isEqualTo("PASS");
  }

  @Test
  void aPaymentThatDoesNotMatchWhatWasSettledIsReported() {
    settleAprilFor(TKF100);
    insertManagementFeePayment(
        TKF100, PAYMENT_DATE, settledFor(TKF100).add(new BigDecimal("50.00")));

    runCashChecksFor(TKF100);

    assertThat(severityOf(TKF100, "CASH_SETTLEMENT_OBSERVED", "MANAGEMENT")).isEqualTo("WARNING");
  }

  @Test
  void aSettlementWithNoPaymentOnceTheWindowClosedIsReported() {
    settleAprilFor(TKF100);

    runCashChecksFor(TKF100);

    assertThat(severityOf(TKF100, "CASH_SETTLEMENT_OBSERVED", "MANAGEMENT")).isEqualTo("WARNING");
  }

  // The ledger carries no fee month on a payment, so a second one in the window cannot be
  // attributed to a month - reported rather than summed, even when the sum would have matched.
  @Test
  void twoPaymentsSummingToTheSettledAmountAreStillReported() {
    settleAprilFor(TKF100);
    var half = settledFor(TKF100).divide(new BigDecimal("2"), 2, RoundingMode.DOWN);
    insertManagementFeePayment(TKF100, PAYMENT_DATE, half);
    insertManagementFeePayment(TKF100, PAYMENT_DATE, settledFor(TKF100).subtract(half));

    runCashChecksFor(TKF100);

    assertThat(severityOf(TKF100, "CASH_SETTLEMENT_OBSERVED", "MANAGEMENT")).isEqualTo("WARNING");
  }

  private void settleAprilFor(TulevaFund fund) {
    accrueFor(fund, LAST_DAY_OF_APRIL);
    accrueFor(fund, FIRST_DAY_OF_MAY);
    insertSystemAccount("MANAGEMENT_FEE:" + fund.name(), "EXPENSE");
  }

  private BigDecimal settledFor(TulevaFund fund) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            JOIN ledger.transaction t ON e.transaction_id = t.id
            WHERE a.name = :accountName
              AND t.transaction_type = CAST('FEE_SETTLEMENT' AS ledger.transaction_type)
            """)
        .param("accountName", "MANAGEMENT_FEE_ACCRUAL:" + fund.name())
        .query(BigDecimal.class)
        .single();
  }

  private void insertManagementFeePayment(TulevaFund fund, LocalDate date, BigDecimal amount) {
    var transactionId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.transaction (id, transaction_type, transaction_date, metadata)
            VALUES (:id, CAST('MANAGEMENT_FEE_PAYMENT' AS ledger.transaction_type), :date,
                    CAST('{"operationType":"MANAGEMENT_FEE_PAYMENT"}' AS jsonb))
            """)
        .param("id", transactionId)
        .param("date", Timestamp.from(instantAt(date)))
        .update();
    insertEntry(transactionId, accountId("MANAGEMENT_FEE:" + fund.name()), amount);
  }

  private void insertSystemAccount(String name, String accountType) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.account (id, name, purpose, asset_type, account_type)
            VALUES (:id, :name, CAST('SYSTEM_ACCOUNT' AS ledger.account_purpose),
                    CAST('EUR' AS ledger.asset_type), CAST(:accountType AS ledger.account_type))
            """)
        .param("id", UUID.randomUUID())
        .param("name", name)
        .param("accountType", accountType)
        .update();
  }

  private void runCashChecksFor(TulevaFund fund) {
    feeCheckService.runMonthlyChecks(List.of(fund), MAY, APRIL, THIRD_BUSINESS_DAY_OF_JUNE);
  }

  private String severityOf(TulevaFund fund, String checkType, String feeScope) {
    return jdbcClient
        .sql(
            """
            SELECT severity FROM investment_fee_check_event
            WHERE fund_code = :fundCode AND check_type = :checkType AND fee_scope = :feeScope
            ORDER BY created_at DESC, id DESC
            """)
        .param("fundCode", fund.name())
        .param("checkType", checkType)
        .param("feeScope", feeScope)
        .query(String.class)
        .list()
        .getFirst();
  }

  private void runMonthlyChecks() {
    feeCheckService.runMonthlyChecks(List.of(TUK75), MAY, APRIL, THIRD_BUSINESS_DAY_OF_JUNE);
  }

  private void accrueFor(LocalDate date) {
    accrueFor(TUK75, date);
  }

  private void accrueFor(TulevaFund fund, LocalDate date) {
    feeCalculationService.calculateFeesForNav(
        fund, date, new FeeBases(BASE_VALUE, BASE_VALUE), null);
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
