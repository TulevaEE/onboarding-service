package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeCalculationService;
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
class FeeCheckIntegrationTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final LocalDate DAY_ONE = LocalDate.of(2026, 6, 2);
  private static final LocalDate DAY_TWO = LocalDate.of(2026, 6, 3);
  private static final LocalDate DAY_THREE = LocalDate.of(2026, 6, 4);
  private static final LocalDate SECURITY_POSITION_DATE = LocalDate.of(2026, 5, 29);
  private static final BigDecimal BASE_VALUE = new BigDecimal("1000000000");
  private static final BigDecimal CORRECTION = new BigDecimal("5.00");
  private static final BigDecimal CUSTODIAN_CASH = new BigDecimal("500000.00");
  private static final BigDecimal TRADE_PAYABLES = new BigDecimal("-91782.00");
  private static final BigDecimal PENDING_REDEMPTIONS = new BigDecimal("-12000.00");

  @Autowired private FeeCheckService feeCheckService;
  @Autowired private FeeCalculationService feeCalculationService;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private OperationsNotificationService notificationService;

  @BeforeEach
  void setUp() {
    insertSecurityPositions();
    insertFeeRates();
    insertDepotFeeTiers();
  }

  @Test
  void manualAccrualCorrectionWithoutLedgerCorrectionIsDetected() {
    accrueFor(DAY_ONE);
    accrueFor(DAY_TWO);
    accrueFor(DAY_THREE);

    correctAccrualWithoutTouchingLedger(DAY_TWO, CORRECTION);

    feeCheckService.runDailyChecks(List.of(TUK75), DAY_THREE);

    var event = findEvent(TUK75, "LEDGER_ACCRUAL_CONSISTENCY", "MANAGEMENT");
    assertThat(event.get("severity")).isEqualTo("FAIL");
    assertThat(event.get("deviation_found")).isEqualTo(true);
    assertThat((BigDecimal) event.get("deviation_amount")).isEqualByComparingTo(CORRECTION);
    assertThat(asText(event.get("result"))).contains(DAY_TWO.toString());
  }

  @Test
  void feeBaseMissingBlackrockAndRedemptionsIsDetected() {
    var blackrockAdjustment = new BigDecimal("56980.96");
    var pendingRedemptions = new BigDecimal("12000.00");
    var securities = new BigDecimal("980000000.00");

    insertNavReportRow(DAY_THREE, "SECURITY", "Security", securities);
    insertNavReportRow(DAY_THREE, "RECEIVABLES", "Other receivables", blackrockAdjustment);
    insertNavReportRow(
        DAY_THREE, "LIABILITY", "Payables of redeemed units", pendingRedemptions.negate());

    var buggyBase = securities;
    insertAccrual(TUK75, "MANAGEMENT", DAY_THREE, buggyBase, new BigDecimal("6712.33"));
    insertAccrual(TUK75, "DEPOT", DAY_THREE, buggyBase, new BigDecimal("268.49"));

    feeCheckService.runDailyChecks(List.of(TUK75), DAY_THREE);

    var event = findEvent(TUK75, "FEE_BASE_COMPLETENESS", "ALL");
    assertThat(event.get("severity")).isEqualTo("FAIL");
    assertThat((BigDecimal) event.get("deviation_amount"))
        .isEqualByComparingTo(blackrockAdjustment.subtract(pendingRedemptions));
  }

  @Test
  void adjustmentTransactionsAreNotTreatedAsAccrualOrphans() {
    accrueFor(DAY_ONE);
    insertFeeBaseValueCorrectionAdjustment(DAY_ONE, new BigDecimal("14.75"));

    feeCheckService.runDailyChecks(List.of(TUK75), DAY_ONE);

    var event = findEvent(TUK75, "LEDGER_ACCRUAL_CONSISTENCY", "MANAGEMENT");
    assertThat(event.get("severity")).isEqualTo("PASS");
    assertThat(event.get("deviation_found")).isEqualTo(false);
  }

  @Test
  void aCustodianLiabilityTheNavNeverRecognisedIsDetected() {
    var unrecognisedLiability = new BigDecimal("-8400.00");

    seedMatchingCustodianPositionsAndNavReport(DAY_THREE);
    insertPosition(DAY_THREE, "LIABILITY", "Accrued expenses payable", null, unrecognisedLiability);

    feeCheckService.runDailyChecks(List.of(TUK75), DAY_THREE);

    var event = findEvent(TUK75, "CUSTODIAN_POSITION_COMPLETENESS", "ALL");
    assertThat(event.get("severity")).isEqualTo("WARNING");
    assertThat((BigDecimal) event.get("deviation_amount"))
        .isEqualByComparingTo(unrecognisedLiability.abs());
  }

  @Test
  void cleanRunProducesPassEventsAndNoSlackMessage() {
    accrueFor(DAY_ONE);
    seedMatchingCustodianPositionsAndNavReport(DAY_ONE);
    insertSystemAccount("BLACKROCK_ADJUSTMENT:TUK75", "ASSET");
    insertBlackrockAdjustment(DAY_ONE, new BigDecimal("100.00"));

    feeCheckService.runDailyChecks(List.of(TUK75), DAY_ONE);

    assertThat(findEvent(TUK75, "LEDGER_ACCRUAL_CONSISTENCY", "MANAGEMENT").get("severity"))
        .isEqualTo("PASS");
    assertThat(findEvent(TUK75, "LEDGER_ACCRUAL_CONSISTENCY", "DEPOT").get("severity"))
        .isEqualTo("PASS");
    assertThat(findEvent(TUK75, "FEE_BASE_COMPLETENESS", "ALL").get("severity")).isEqualTo("PASS");
    assertThat(findEvent(TUK75, "CUSTODIAN_POSITION_COMPLETENESS", "ALL").get("severity"))
        .isEqualTo("PASS");
    verifyNoInteractions(notificationService);
  }

  // The redemptions row is reported by the custodian but sourced from our own register, so it has
  // to drop out of both sides. Seeding it non-zero is what proves the exclusion is symmetric.
  private void seedMatchingCustodianPositionsAndNavReport(LocalDate navDate) {
    var securities =
        BASE_VALUE.subtract(CUSTODIAN_CASH).subtract(TRADE_PAYABLES).subtract(PENDING_REDEMPTIONS);

    insertPosition(navDate, "CASH", "Cash account in SEB Pank", null, CUSTODIAN_CASH);
    insertPosition(
        navDate, "LIABILITY", "Total payables of unsettled transactions", null, TRADE_PAYABLES);
    insertPosition(
        navDate, "LIABILITY", "Payables of redeemed units", TUK75.getIsin(), PENDING_REDEMPTIONS);

    insertNavReportRow(navDate, "SECURITY", "Security", securities);
    insertNavReportRow(navDate, "CASH", "Cash account in SEB Pank", CUSTODIAN_CASH);
    insertNavReportRow(
        navDate, "LIABILITY", "Total payables of unsettled transactions", TRADE_PAYABLES);
    insertNavReportRow(navDate, "LIABILITY", "Payables of redeemed units", PENDING_REDEMPTIONS);
  }

  private void insertPosition(
      LocalDate navDate,
      String accountType,
      String accountName,
      String accountId,
      BigDecimal marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
            (nav_date, fund_code, account_type, account_name, account_id, market_value)
            VALUES (:navDate, 'TUK75', :accountType, :accountName, :accountId, :marketValue)
            """)
        .param("navDate", navDate)
        .param("accountType", accountType)
        .param("accountName", accountName)
        .param("accountId", accountId)
        .param("marketValue", marketValue)
        .update();
  }

  // Codex found this: the event row was saved before the send, so a Slack outage during the first
  // FAIL made the next run read FAIL-vs-FAIL and stay quiet. A genuine deviation went silent for
  // good. Rows from an undelivered run are now skipped when looking for the previous severity.
  @Test
  void aDeviationFirstSeenWhileSlackWasDownStillAlertsOnTheNextRun() {
    accrueFor(DAY_ONE);
    seedMatchingCustodianPositionsAndNavReport(DAY_ONE);
    insertSystemAccount("BLACKROCK_ADJUSTMENT:TUK75", "ASSET");
    insertBlackrockAdjustment(DAY_ONE, new BigDecimal("100.00"));
    feeCheckService.runDailyChecks(List.of(TUK75), DAY_ONE);

    correctAccrualWithoutTouchingLedger(DAY_ONE, CORRECTION);
    doThrow(new RuntimeException("slack is down"))
        .when(notificationService)
        .sendMessage(any(), any());
    feeCheckService.runDailyChecks(List.of(TUK75), DAY_ONE);

    reset(notificationService);
    feeCheckService.runDailyChecks(List.of(TUK75), DAY_ONE);

    verify(notificationService).sendMessage(any(), any());
  }

  private String asText(Object jsonbValue) {
    return jsonbValue instanceof byte[] bytes
        ? new String(bytes, UTF_8)
        : String.valueOf(jsonbValue);
  }

  private void accrueFor(LocalDate date) {
    var cutoff = date.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    feeCalculationService.calculateFeesForNav(TUK75, date, BASE_VALUE, cutoff, null);
  }

  private void correctAccrualWithoutTouchingLedger(LocalDate date, BigDecimal amount) {
    jdbcClient
        .sql(
            """
            UPDATE investment_fee_accrual
            SET daily_amount_net = daily_amount_net + :amount,
                daily_amount_gross = daily_amount_gross + :amount
            WHERE fund_code = 'TUK75' AND fee_type = 'MANAGEMENT' AND accrual_date = :date
            """)
        .param("amount", amount)
        .param("date", date)
        .update();
  }

  private Map<String, Object> findEvent(TulevaFund fund, String checkType, String feeScope) {
    return jdbcClient
        .sql(
            """
            SELECT severity, deviation_found, deviation_amount, result
            FROM investment_fee_check_event
            WHERE fund_code = :fundCode AND check_type = :checkType AND fee_scope = :feeScope
            ORDER BY created_at DESC, id DESC
            """)
        .param("fundCode", fund.name())
        .param("checkType", checkType)
        .param("feeScope", feeScope)
        .query()
        .listOfRows()
        .getFirst();
  }

  private void insertNavReportRow(
      LocalDate navDate, String accountType, String accountName, BigDecimal marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
            (nav_date, fund_code, account_type, account_name, market_value, calculation_id)
            VALUES (:navDate, 'TUK75', :accountType, :accountName, :marketValue, :calculationId)
            """)
        .param("navDate", navDate)
        .param("accountType", accountType)
        .param("accountName", accountName)
        .param("marketValue", marketValue)
        .param("calculationId", UUID.nameUUIDFromBytes("test-calc".getBytes()))
        .update();
  }

  private void insertAccrual(
      TulevaFund fund,
      String feeType,
      LocalDate accrualDate,
      BigDecimal baseValue,
      BigDecimal dailyNet) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_accrual
            (fund_code, fee_type, accrual_date, fee_month, base_value, annual_rate,
             daily_amount_net, daily_amount_gross, days_in_year, reference_date)
            VALUES (:fundCode, :feeType, :accrualDate, :feeMonth, :baseValue, 0.0025,
                    :dailyNet, :dailyNet, 365, :accrualDate)
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType)
        .param("accrualDate", accrualDate)
        .param("feeMonth", accrualDate.withDayOfMonth(1))
        .param("baseValue", baseValue)
        .param("dailyNet", dailyNet)
        .update();
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

  private void insertBlackrockAdjustment(LocalDate date, BigDecimal amount) {
    var transactionId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.transaction (id, transaction_type, transaction_date, metadata)
            VALUES (:id, CAST('ADJUSTMENT' AS ledger.transaction_type), :date,
                    CAST('{"operationType":"BLACKROCK_ADJUSTMENT","fund":"TUK75"}' AS jsonb))
            """)
        .param("id", transactionId)
        .param("date", Timestamp.from(instantAt(date)))
        .update();
    insertEntry(transactionId, accountId("BLACKROCK_ADJUSTMENT:TUK75"), amount);
    insertEntry(transactionId, accountId("NAV_EQUITY:TUK75"), amount.negate());
  }

  private void insertFeeBaseValueCorrectionAdjustment(LocalDate date, BigDecimal amount) {
    var transactionId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.transaction (id, transaction_type, transaction_date, metadata)
            VALUES (:id, CAST('ADJUSTMENT' AS ledger.transaction_type), :date,
                    CAST('{"operationType":"FEE_BASE_VALUE_CORRECTION","fund":"TUK75"}' AS jsonb))
            """)
        .param("id", transactionId)
        .param("date", Timestamp.from(instantAt(date)))
        .update();
    insertEntry(transactionId, accountId("MANAGEMENT_FEE_ACCRUAL:TUK75"), amount.negate());
    insertEntry(transactionId, accountId("NAV_EQUITY:TUK75"), amount);
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

  // The custodian check walks every position date in its window, so this depot-fee-tier fixture
  // needs the nav_report row that a real position date would have had. Without it the date is a
  // day the check could not compare, which is a coverage gap rather than a clean run.
  private void insertSecurityPositions() {
    for (TulevaFund fund : TulevaFund.values()) {
      jdbcClient
          .sql(
              """
              INSERT INTO investment_fund_position
              (nav_date, fund_code, account_type, account_name, account_id, market_value)
              VALUES (:navDate, :fundCode, 'SECURITY', :accountId, :accountId, 980000000)
              """)
          .param("navDate", SECURITY_POSITION_DATE)
          .param("fundCode", fund.name())
          .param("accountId", "TEST_ISIN_" + fund.name())
          .update();
    }
    insertNavReportRow(SECURITY_POSITION_DATE, "SECURITY", "Security", new BigDecimal("980000000"));
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
