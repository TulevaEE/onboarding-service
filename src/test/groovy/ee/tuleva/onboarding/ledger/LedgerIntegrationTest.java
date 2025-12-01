package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ZERO;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class LedgerIntegrationTest {

  @Autowired private LedgerService ledgerService;
  @Autowired private LedgerAccountRepository ledgerAccountRepository;

  @Test
  void getBalanceAt_returnsBalanceUpToAndIncludingDate() {
    var account = createTestAccount();
    var now = Instant.now();
    var yesterday = now.minus(1, DAYS);
    var twoDaysAgo = now.minus(2, DAYS);
    var tomorrow = now.plus(1, DAYS);

    addTransaction(account, twoDaysAgo, new BigDecimal("-100.00"));
    addTransaction(account, yesterday, new BigDecimal("-50.00"));
    addTransaction(account, tomorrow, new BigDecimal("-25.00"));

    assertThat(account.getBalanceAt(twoDaysAgo)).isEqualTo(new BigDecimal("-100.00"));
    assertThat(account.getBalanceAt(yesterday)).isEqualTo(new BigDecimal("-150.00"));
    assertThat(account.getBalanceAt(now)).isEqualTo(new BigDecimal("-150.00"));
    assertThat(account.getBalanceAt(tomorrow)).isEqualTo(new BigDecimal("-175.00"));
    assertThat(account.getBalance()).isEqualTo(new BigDecimal("-175.00"));
  }

  @Test
  void getBalanceBetween_returnsBalanceWithinDateRange() {
    var account = createTestAccount();
    var now = Instant.now();
    var yesterday = now.minus(1, DAYS);
    var twoDaysAgo = now.minus(2, DAYS);
    var threeDaysAgo = now.minus(3, DAYS);
    var tomorrow = now.plus(1, DAYS);

    addTransaction(account, threeDaysAgo, new BigDecimal("-100.00"));
    addTransaction(account, twoDaysAgo, new BigDecimal("-50.00"));
    addTransaction(account, yesterday, new BigDecimal("-25.00"));
    addTransaction(account, tomorrow, new BigDecimal("-10.00"));

    assertThat(account.getBalanceBetween(threeDaysAgo, twoDaysAgo))
        .isEqualTo(new BigDecimal("-150.00"));
    assertThat(account.getBalanceBetween(twoDaysAgo, yesterday))
        .isEqualTo(new BigDecimal("-75.00"));
    assertThat(account.getBalanceBetween(yesterday, tomorrow)).isEqualTo(new BigDecimal("-35.00"));
    assertThat(account.getBalanceBetween(yesterday, yesterday)).isEqualTo(new BigDecimal("-25.00"));
    assertThat(account.getBalanceBetween(threeDaysAgo, tomorrow))
        .isEqualTo(new BigDecimal("-185.00"));
  }

  @Test
  void emptyAccount_returnsZeroBalance() {
    var account = createTestAccount();
    var now = Instant.now();
    var yesterday = now.minus(1, DAYS);

    assertThat(account.getBalance()).isEqualTo(ZERO);
    assertThat(account.getBalanceAt(now)).isEqualTo(ZERO);
    assertThat(account.getBalanceBetween(yesterday, now)).isEqualTo(ZERO);
  }

  @Test
  void multipleUsers_canHaveSameAccountTypes() {
    var user1 = sampleUser().personalCode("11111111111").build();
    var user2 = sampleUser().personalCode("22222222222").build();

    var fundUnitsAccount1 = ledgerService.getUserAccount(user1, FUND_UNITS);
    var fundUnitsAccount2 = ledgerService.getUserAccount(user2, FUND_UNITS);
    var cashAccount1 = ledgerService.getUserAccount(user1, CASH);
    var cashAccount2 = ledgerService.getUserAccount(user2, CASH);

    assertThat(fundUnitsAccount1.getId()).isNotEqualTo(fundUnitsAccount2.getId());
    assertThat(cashAccount1.getId()).isNotEqualTo(cashAccount2.getId());
  }

  private LedgerAccount createTestAccount() {
    return ledgerAccountRepository.save(
        LedgerAccount.builder()
            .name("TEST_ACCOUNT")
            .purpose(SYSTEM_ACCOUNT)
            .accountType(LIABILITY)
            .assetType(EUR)
            .build());
  }

  private void addTransaction(LedgerAccount account, Instant date, BigDecimal amount) {
    var transaction = LedgerTransaction.builder().transactionDate(date).metadata(Map.of()).build();
    transaction.addEntry(account, amount);
  }
}
