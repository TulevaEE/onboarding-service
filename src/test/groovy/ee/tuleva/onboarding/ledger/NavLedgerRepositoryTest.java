package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.SECURITIES_UNITS;
import static ee.tuleva.onboarding.ledger.SystemAccount.SECURITIES_UNITS_EQUITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
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

    Map<String, BigDecimal> balances = navLedgerRepository.getSecuritiesUnitBalances();

    assertThat(balances).hasSize(2);
    assertThat(balances.get("IE00BFG1TM61")).isEqualByComparingTo("1000.00000");
    assertThat(balances.get("IE00BMDBMY19")).isEqualByComparingTo("500.00000");
  }

  @Test
  void getSecuritiesUnitBalances_returnsEmptyMap_whenNoEntries() {
    Map<String, BigDecimal> balances = navLedgerRepository.getSecuritiesUnitBalances();

    assertThat(balances).isEmpty();
  }

  @Test
  void getSecuritiesUnitBalances_sumsMultipleEntriesPerIsin() {
    createSecuritiesUnitsBalance("IE00BFG1TM61", new BigDecimal("1000.00000"));
    createSecuritiesUnitsBalance("IE00BFG1TM61", new BigDecimal("200.00000"));
    entityManager.flush();

    Map<String, BigDecimal> balances = navLedgerRepository.getSecuritiesUnitBalances();

    assertThat(balances).hasSize(1);
    assertThat(balances.get("IE00BFG1TM61")).isEqualByComparingTo("1200.00000");
  }

  private void createSecuritiesUnitsBalance(String isin, BigDecimal amount) {
    String accountName = SECURITIES_UNITS.getAccountName(isin);
    String equityAccountName = SECURITIES_UNITS_EQUITY.getAccountName(isin);
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
