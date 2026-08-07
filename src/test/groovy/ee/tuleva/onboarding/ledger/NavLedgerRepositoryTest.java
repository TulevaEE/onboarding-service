package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class NavLedgerRepositoryTest {

  @Autowired NavLedgerRepository navLedgerRepository;
  @Autowired LedgerAccountService ledgerAccountService;
  @Autowired EntityManager entityManager;

  @Test
  void getSecuritiesUnitBalances_returnsIsinToBalanceMap() {
    createSecuritiesUnitsBalance("IE00BFG1TM61", new BigDecimal("1000.00000"));
    createSecuritiesUnitsBalance("IE00BMDBMY19", new BigDecimal("500.00000"));
    entityManager.flush();

    Map<String, BigDecimal> balances = navLedgerRepository.getSecuritiesUnitBalances(TKF100);

    assertThat(balances).hasSize(2);
    assertThat(balances.get("IE00BFG1TM61")).isEqualByComparingTo("1000.00000");
    assertThat(balances.get("IE00BMDBMY19")).isEqualByComparingTo("500.00000");
  }

  @Test
  void getSecuritiesUnitBalances_returnsEmptyMap_whenNoEntries() {
    Map<String, BigDecimal> balances = navLedgerRepository.getSecuritiesUnitBalances(TKF100);

    assertThat(balances).isEmpty();
  }

  @Test
  void getSecuritiesUnitBalances_sumsMultipleEntriesPerIsin() {
    createSecuritiesUnitsBalance("IE00BFG1TM61", new BigDecimal("1000.00000"));
    createSecuritiesUnitsBalance("IE00BFG1TM61", new BigDecimal("200.00000"));
    entityManager.flush();

    Map<String, BigDecimal> balances = navLedgerRepository.getSecuritiesUnitBalances(TKF100);

    assertThat(balances).hasSize(1);
    assertThat(balances.get("IE00BFG1TM61")).isEqualByComparingTo("1200.00000");
  }

  @Test
  void getSecuritiesUnitBalancesAt_excludesFullyLiquidatedInstruments() {
    createSecuritiesUnitsBalance("IE00BFG1TM61", new BigDecimal("1000.00000"));
    createSecuritiesUnitsBalance("IE00BFNM3D14", new BigDecimal("500.00000"));
    createSecuritiesUnitsBalance("IE00BFNM3D14", new BigDecimal("-500.00000"));
    entityManager.flush();

    Instant cutoff = Instant.now().plusSeconds(3600);

    Map<String, BigDecimal> balances =
        navLedgerRepository.getSecuritiesUnitBalancesAt(cutoff, TKF100);

    assertThat(balances).hasSize(1);
    assertThat(balances.get("IE00BFG1TM61")).isEqualByComparingTo("1000.00000");
    assertThat(balances).doesNotContainKey("IE00BFNM3D14");
  }

  @Test
  void getSystemAccountBalanceBefore_excludesEntriesAtOrAfterCutoff() {
    ZoneId eet = ZoneId.of("Europe/Tallinn");
    LedgerAccount feeAccount =
        ledgerAccountService
            .findSystemAccount(MANAGEMENT_FEE_ACCRUAL, TKF100)
            .orElseGet(
                () -> ledgerAccountService.createSystemAccount(MANAGEMENT_FEE_ACCRUAL, TKF100));
    LedgerAccount equityAccount =
        ledgerAccountService
            .findSystemAccount(NAV_EQUITY, TKF100)
            .orElseGet(() -> ledgerAccountService.createSystemAccount(NAV_EQUITY, TKF100));

    Instant feb25 = LocalDate.of(2026, 2, 25).atTime(12, 0).atZone(eet).toInstant();
    Instant feb26 = LocalDate.of(2026, 2, 26).atTime(12, 0).atZone(eet).toInstant();
    Instant feb28 = LocalDate.of(2026, 2, 28).atTime(12, 0).atZone(eet).toInstant();

    createSystemAccountEntry(feeAccount, equityAccount, new BigDecimal("-100.00"), feb25);
    createSystemAccountEntry(feeAccount, equityAccount, new BigDecimal("-100.00"), feb26);
    createSystemAccountEntry(feeAccount, equityAccount, new BigDecimal("-100.00"), feb28);
    entityManager.flush();

    Instant cutoff = LocalDate.of(2026, 2, 28).atStartOfDay().atZone(eet).toInstant();

    BigDecimal balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100), cutoff);

    assertThat(balance).isEqualByComparingTo("-200.00");
  }

  @Test
  void getSystemAccountBalanceBefore_returnsZeroWhenNoEntries() {
    Instant cutoff = Instant.now();

    BigDecimal balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100), cutoff);

    assertThat(balance).isEqualByComparingTo("0");
  }

  @Test
  void findEntriesByTransactionTypeBetween_returnsOnlyMatchingTypeInsideTheHalfOpenWindow() {
    var account = feeAccrualAccount();
    var counter = navEquityAccount();
    var inside = instantAt("2026-06-02");
    var atUpperBound = instantAt("2026-06-04");

    createSystemAccountEntry(account, counter, new BigDecimal("-5.89"), inside, FEE_ACCRUAL);
    createSystemAccountEntry(
        account, counter, new BigDecimal("-1.11"), instantAt("2026-06-03"), FEE_ACCRUAL);
    createSystemAccountEntry(account, counter, new BigDecimal("-9.99"), inside, ADJUSTMENT);
    createSystemAccountEntry(account, counter, new BigDecimal("-7.77"), atUpperBound, FEE_ACCRUAL);
    entityManager.flush();

    var entries =
        navLedgerRepository.findEntriesByTransactionTypeBetween(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100),
            FEE_ACCRUAL,
            instantAt("2026-06-02"),
            atUpperBound);

    assertThat(entries)
        .extracting(LedgerEntryAmount::transactionDate, LedgerEntryAmount::amount)
        .containsExactly(
            tuple(inside, new BigDecimal("-5.89")),
            tuple(instantAt("2026-06-03"), new BigDecimal("-1.11")));
  }

  @Test
  void findLatestTransactionDateByType_returnsTheMostRecentMatchingTransaction() {
    var account = feeAccrualAccount();
    var counter = navEquityAccount();
    createSystemAccountEntry(
        account, counter, new BigDecimal("-1.00"), instantAt("2026-06-02"), ADJUSTMENT);
    createSystemAccountEntry(
        account, counter, new BigDecimal("-2.00"), instantAt("2026-06-05"), ADJUSTMENT);
    createSystemAccountEntry(
        account, counter, new BigDecimal("-3.00"), instantAt("2026-06-09"), FEE_ACCRUAL);
    entityManager.flush();

    var latest =
        navLedgerRepository.findLatestTransactionDateByType(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100), ADJUSTMENT);

    assertThat(latest).contains(instantAt("2026-06-05"));
  }

  @Test
  void findLatestTransactionDateByType_isEmptyWhenNothingWasEverPosted() {
    var latest =
        navLedgerRepository.findLatestTransactionDateByType(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100), ADJUSTMENT);

    assertThat(latest).isEmpty();
  }

  private LedgerAccount feeAccrualAccount() {
    return systemAccount(MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100));
  }

  private LedgerAccount navEquityAccount() {
    return systemAccount(NAV_EQUITY.getAccountName(TKF100));
  }

  private LedgerAccount systemAccount(String name) {
    return ledgerAccountService
        .findSystemAccountByName(name, LIABILITY, EUR)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(name, LIABILITY, EUR));
  }

  private Instant instantAt(String date) {
    return LocalDate.parse(date).atTime(9, 0).atZone(ZoneId.of("Europe/Tallinn")).toInstant();
  }

  private void createSystemAccountEntry(
      LedgerAccount account,
      LedgerAccount counterAccount,
      BigDecimal amount,
      Instant timestamp,
      LedgerTransaction.TransactionType transactionType) {
    var transaction =
        LedgerTransaction.builder()
            .transactionType(transactionType)
            .transactionDate(timestamp)
            .build();

    var entry =
        LedgerEntry.builder()
            .amount(amount)
            .assetType(EUR)
            .account(account)
            .transaction(transaction)
            .build();
    var counterEntry =
        LedgerEntry.builder()
            .amount(amount.negate())
            .assetType(EUR)
            .account(counterAccount)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry);
    transaction.getEntries().add(counterEntry);
    entityManager.persist(transaction);
  }

  private void createSystemAccountEntry(
      LedgerAccount account, LedgerAccount counterAccount, BigDecimal amount, Instant timestamp) {
    var transaction =
        LedgerTransaction.builder().transactionType(ADJUSTMENT).transactionDate(timestamp).build();

    var entry =
        LedgerEntry.builder()
            .amount(amount)
            .assetType(EUR)
            .account(account)
            .transaction(transaction)
            .build();

    var counterEntry =
        LedgerEntry.builder()
            .amount(amount.negate())
            .assetType(EUR)
            .account(counterAccount)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry);
    transaction.getEntries().add(counterEntry);
    entityManager.persist(transaction);
  }

  private void createSecuritiesUnitsBalance(String isin, BigDecimal amount) {
    String accountName = SECURITIES_UNITS.getAccountName(TKF100, isin);
    String equityAccountName = SECURITIES_UNITS_EQUITY.getAccountName(TKF100, isin);
    LedgerAccount account =
        ledgerAccountService
            .findSystemAccountByName(accountName, ASSET, FUND_UNIT)
            .orElseGet(
                () -> ledgerAccountService.createSystemAccount(accountName, ASSET, FUND_UNIT));
    LedgerAccount equityAccount =
        ledgerAccountService
            .findSystemAccountByName(equityAccountName, LIABILITY, FUND_UNIT)
            .orElseGet(
                () ->
                    ledgerAccountService.createSystemAccount(
                        equityAccountName, LIABILITY, FUND_UNIT));

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .build();

    var entry =
        LedgerEntry.builder()
            .amount(amount)
            .assetType(FUND_UNIT)
            .account(account)
            .transaction(transaction)
            .build();

    var counterEntry =
        LedgerEntry.builder()
            .amount(amount.negate())
            .assetType(FUND_UNIT)
            .account(equityAccount)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry);
    transaction.getEntries().add(counterEntry);
    entityManager.persist(transaction);
  }
}
