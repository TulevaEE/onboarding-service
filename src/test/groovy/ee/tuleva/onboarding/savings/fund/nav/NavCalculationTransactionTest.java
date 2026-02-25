package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.position.AccountType.NAV;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class NavCalculationTransactionTest {

  @Autowired NavCalculationService navCalculationService;
  @Autowired LedgerService ledgerService;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired EntityManager entityManager;
  @Autowired TransactionTemplate transactionTemplate;

  static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 2, 4);
  static final LocalDate NAV_DATE = LocalDate.of(2026, 2, 3);
  static final Instant TRANSACTION_DATE =
      NAV_DATE.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();

  @AfterEach
  void cleanup() {
    transactionTemplate.executeWithoutResult(
        status -> {
          entityManager.createNativeQuery("DELETE FROM ledger.entry").executeUpdate();
          entityManager.createNativeQuery("DELETE FROM ledger.transaction").executeUpdate();
          entityManager.createNativeQuery("DELETE FROM ledger.account").executeUpdate();
          entityManager.createNativeQuery("DELETE FROM ledger.party").executeUpdate();
          entityManager.createNativeQuery("DELETE FROM investment_fund_position").executeUpdate();
        });
  }

  @Test
  void calculateOutsideTransaction_shouldNotThrowLazyInitializationException() {
    transactionTemplate.executeWithoutResult(
        status -> {
          setupFundPosition();
          createSystemAccountBalances();
          createFundUnitsOutstanding();
          entityManager.flush();
        });

    var result = navCalculationService.calculate(TKF100, CALCULATION_DATE);

    assertThat(result.cashPosition()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(result.receivables()).isEqualByComparingTo(new BigDecimal("200.00"));
    assertThat(result.payables()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(result.blackrockAdjustment()).isEqualByComparingTo(ZERO);
    assertThat(result.unitsOutstanding()).isEqualByComparingTo(new BigDecimal("500.00000"));
    assertThat(result.navPerUnit().signum()).isPositive();
  }

  private void setupFundPosition() {
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(NAV_DATE)
            .fund(TKF100)
            .accountType(NAV)
            .accountName("Net Asset Value")
            .accountId("EE0000003283")
            .quantity(ONE)
            .marketPrice(ONE)
            .currency("EUR")
            .marketValue(ONE)
            .build());
  }

  private void createSystemAccountBalances() {
    createBalanceEntry(CASH_POSITION, new BigDecimal("1000.00"), EUR);
    createBalanceEntry(TRADE_RECEIVABLES, new BigDecimal("200.00"), EUR);
    createBalanceEntry(TRADE_PAYABLES, new BigDecimal("-100.00"), EUR);
    createBalanceEntry(BLACKROCK_ADJUSTMENT, ZERO, EUR);
  }

  private void createBalanceEntry(
      SystemAccount systemAccount, BigDecimal amount, LedgerAccount.AssetType assetType) {
    LedgerAccount account = ledgerService.getSystemAccount(systemAccount);
    LedgerAccount navEquity = ledgerService.getSystemAccount(NAV_EQUITY);

    if (amount.compareTo(ZERO) == 0) {
      return;
    }

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(TRANSACTION_DATE)
            .build();

    transaction
        .getEntries()
        .add(
            LedgerEntry.builder()
                .amount(amount)
                .assetType(assetType)
                .account(account)
                .transaction(transaction)
                .build());

    transaction
        .getEntries()
        .add(
            LedgerEntry.builder()
                .amount(amount.negate())
                .assetType(assetType)
                .account(navEquity)
                .transaction(transaction)
                .build());

    entityManager.persist(transaction);
  }

  private void createFundUnitsOutstanding() {
    var testUser = sampleUser().build();

    LedgerAccount fundUnitsOutstanding = ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING);
    LedgerAccount userFundUnits = ledgerService.getUserAccount(testUser, FUND_UNITS);

    BigDecimal units = new BigDecimal("500.00000");

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(TRANSACTION_DATE)
            .build();

    transaction
        .getEntries()
        .add(
            LedgerEntry.builder()
                .amount(units)
                .assetType(FUND_UNIT)
                .account(fundUnitsOutstanding)
                .transaction(transaction)
                .build());

    transaction
        .getEntries()
        .add(
            LedgerEntry.builder()
                .amount(units.negate())
                .assetType(FUND_UNIT)
                .account(userFundUnits)
                .transaction(transaction)
                .build());

    entityManager.persist(transaction);
  }
}
