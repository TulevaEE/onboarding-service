package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPositionImportService.ImportResult;
import ee.tuleva.onboarding.ledger.SystemAccount;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FundPositionUpsertIntegrationTest {

  static final LocalDate NAV_DATE = LocalDate.of(2026, 2, 5);
  static final String ISIN = "IE00BFG1TM61";

  @Autowired FundPositionImportService importService;
  @Autowired FundPositionRepository positionRepository;
  @Autowired FundPositionLedgerService ledgerService;
  @Autowired JdbcClient jdbcClient;
  @Autowired EntityManager entityManager;

  @Test
  void firstImport_savesPositionsAndRecordsLedger() {
    importService.importNewPositions(originalPositions());
    entityManager.flush();

    ledgerService.recordPositionsToLedger(TKF100, NAV_DATE);
    entityManager.flush();

    var cashPositions =
        positionRepository.findByNavDateAndFundAndAccountType(NAV_DATE, TKF100, CASH);
    assertThat(cashPositions).hasSize(1);
    assertThat(cashPositions.get(0).getMarketValue()).isEqualByComparingTo("5000000");
    assertThat(cashPositions.get(0).getUpdatedAt()).isNull();

    var securityPositions =
        positionRepository.findByNavDateAndFundAndAccountType(NAV_DATE, TKF100, SECURITY);
    assertThat(securityPositions).hasSize(1);
    assertThat(securityPositions.get(0).getQuantity()).isEqualByComparingTo("1000");
    assertThat(securityPositions.get(0).getUpdatedAt()).isNull();

    assertThat(getSystemAccountBalance(CASH_POSITION, TKF100)).isEqualByComparingTo("5000000");
    assertThat(getSecuritiesUnitsBalance(TKF100, ISIN)).isEqualByComparingTo("1000");
  }

  @Test
  void reimportingSameData_doesNotDuplicateOrUpdate() {
    importService.importNewPositions(originalPositions());
    entityManager.flush();
    ledgerService.recordPositionsToLedger(TKF100, NAV_DATE);
    entityManager.flush();

    int result = importService.importNewPositions(originalPositions());
    entityManager.flush();
    ledgerService.recordPositionsToLedger(TKF100, NAV_DATE);
    entityManager.flush();

    assertThat(result).isEqualTo(0);

    var cashPositions =
        positionRepository.findByNavDateAndFundAndAccountType(NAV_DATE, TKF100, CASH);
    assertThat(cashPositions).hasSize(1);
    assertThat(cashPositions.get(0).getUpdatedAt()).isNull();

    int txCount =
        jdbcClient.sql("SELECT COUNT(*) FROM ledger.transaction").query(Integer.class).single();
    assertThat(txCount).isEqualTo(1);
  }

  @Test
  void importNewPositions_skipsExistingPositions() {
    LocalDate olderDate = LocalDate.of(2026, 2, 3);
    importService.importNewPositions(positionsForDate(olderDate, "5000000", "1000", "100000"));
    entityManager.flush();

    int result =
        importService.importNewPositions(positionsForDate(olderDate, "5100000", "1050", "105000"));
    entityManager.flush();

    assertThat(result).isEqualTo(0);

    var cashPositions =
        positionRepository.findByNavDateAndFundAndAccountType(olderDate, TKF100, CASH);
    assertThat(cashPositions).hasSize(1);
    assertThat(cashPositions.get(0).getMarketValue()).isEqualByComparingTo("5000000");
    assertThat(cashPositions.get(0).getUpdatedAt()).isNull();

    var securityPositions =
        positionRepository.findByNavDateAndFundAndAccountType(olderDate, TKF100, SECURITY);
    assertThat(securityPositions).hasSize(1);
    assertThat(securityPositions.get(0).getQuantity()).isEqualByComparingTo("1000");
  }

  @Test
  void correctedReport_updatesPositionsAndLedger() {
    importService.importNewPositions(originalPositions());
    entityManager.flush();
    ledgerService.recordPositionsToLedger(TKF100, NAV_DATE);
    entityManager.flush();

    ImportResult upsertResult = importService.upsertPositions(correctedPositions());
    entityManager.flush();

    assertThat(upsertResult.updated()).isEqualTo(2);
    assertThat(upsertResult.imported()).isEqualTo(0);

    var cashPositions =
        positionRepository.findByNavDateAndFundAndAccountType(NAV_DATE, TKF100, CASH);
    assertThat(cashPositions.get(0).getMarketValue()).isEqualByComparingTo("5100000");
    assertThat(cashPositions.get(0).getUpdatedAt()).isNotNull();

    var securityPositions =
        positionRepository.findByNavDateAndFundAndAccountType(NAV_DATE, TKF100, SECURITY);
    assertThat(securityPositions.get(0).getQuantity()).isEqualByComparingTo("1050");
    assertThat(securityPositions.get(0).getMarketValue()).isEqualByComparingTo("105000");
    assertThat(securityPositions.get(0).getUpdatedAt()).isNotNull();

    ledgerService.rerecordPositionsFromDate(TKF100, NAV_DATE);
    entityManager.flush();

    assertThat(getSystemAccountBalance(CASH_POSITION, TKF100)).isEqualByComparingTo("5100000");
    assertThat(getSecuritiesUnitsBalance(TKF100, ISIN)).isEqualByComparingTo("1050");
  }

  @Test
  void rerecordPositionsFromDate_producesCorrectBalancesAfterRerun() {
    importService.importNewPositions(originalPositions());
    entityManager.flush();
    ledgerService.recordPositionsToLedger(TKF100, NAV_DATE);
    entityManager.flush();

    importService.upsertPositions(correctedPositions());
    entityManager.flush();
    ledgerService.rerecordPositionsFromDate(TKF100, NAV_DATE);
    entityManager.flush();

    assertThat(getSystemAccountBalance(CASH_POSITION, TKF100)).isEqualByComparingTo("5100000");
    assertThat(getSecuritiesUnitsBalance(TKF100, ISIN)).isEqualByComparingTo("1050");

    ledgerService.rerecordPositionsFromDate(TKF100, NAV_DATE);
    entityManager.flush();

    assertThat(getSystemAccountBalance(CASH_POSITION, TKF100)).isEqualByComparingTo("5100000");
    assertThat(getSecuritiesUnitsBalance(TKF100, ISIN)).isEqualByComparingTo("1050");
  }

  private List<FundPosition> originalPositions() {
    return positionsForDate(NAV_DATE, "5000000", "1000", "100000");
  }

  private List<FundPosition> correctedPositions() {
    return positionsForDate(NAV_DATE, "5100000", "1050", "105000");
  }

  private List<FundPosition> positionsForDate(
      LocalDate date, String cashValue, String securityQty, String securityMv) {
    return List.of(
        FundPosition.builder()
            .navDate(date)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Overnight Deposit")
            .quantity(new BigDecimal(cashValue))
            .marketPrice(BigDecimal.ONE)
            .currency("EUR")
            .marketValue(new BigDecimal(cashValue))
            .createdAt(Instant.now())
            .build(),
        FundPosition.builder()
            .navDate(date)
            .fund(TKF100)
            .accountType(SECURITY)
            .accountName("ISHARES DEV WLD ESG")
            .accountId(ISIN)
            .quantity(new BigDecimal(securityQty))
            .marketPrice(new BigDecimal("100"))
            .currency("EUR")
            .marketValue(new BigDecimal(securityMv))
            .createdAt(Instant.now())
            .build());
  }

  private BigDecimal getSystemAccountBalance(SystemAccount systemAccount, TulevaFund fund) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
            """)
        .param("accountName", systemAccount.getAccountName(fund))
        .query(BigDecimal.class)
        .single();
  }

  private BigDecimal getSecuritiesUnitsBalance(TulevaFund fund, String isin) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
            """)
        .param("accountName", SECURITIES_UNITS.getAccountName(fund, isin))
        .query(BigDecimal.class)
        .single();
  }
}
