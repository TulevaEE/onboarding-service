package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.USER;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static java.math.BigDecimal.ZERO;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
public class LedgerIntegrationTest {
  @Autowired private LedgerService ledgerService;

  @Autowired private LedgerAccountRepository ledgerAccountRepository;

  @Autowired private LedgerPartyRepository ledgerPartyRepository;

  @BeforeEach
  void setup() {
    ledgerAccountRepository.save(
        LedgerAccount.builder()
            .name(INCOMING_PAYMENTS_CLEARING.name())
            .purpose(SYSTEM_ACCOUNT)
            .accountType(LIABILITY)
            .assetType(EUR)
            .build());
  }

  @AfterEach
  void cleanup() {
    ledgerAccountRepository.deleteAll();
    ledgerPartyRepository.deleteAll();
  }

  @Test
  @DisplayName("should onboard user")
  public void shouldOnboardUser() {
    User user = sampleUser().build();

    ledgerService.onboard(user);

    var party = ledgerPartyRepository.findByOwnerId(user.getPersonalCode());

    assertThat(party.getOwnerId()).isEqualTo(user.getPersonalCode());
    assertThat(party.getPartyType()).isEqualTo(USER);

    var accounts = ledgerAccountRepository.findAllByOwner(party);

    assertThat(accounts.size()).isEqualTo(7);

    var cashAccount =
        accounts.stream()
            .filter(
                account -> account.getAccountType() == LIABILITY && account.getAssetType() == EUR)
            .findFirst()
            .orElseThrow();
    var stockAccount =
        accounts.stream()
            .filter(
                account ->
                    account.getAccountType() == LIABILITY && account.getAssetType() == FUND_UNIT)
            .findFirst()
            .orElseThrow();

    assertThat(cashAccount).isNotNull();
    assertThat(stockAccount).isNotNull();
  }

  @Test
  @DisplayName("should calculate balance at specific date")
  public void shouldCalculateBalanceAtDate() {
    // Given: Create an account and add transactions at different dates
    LedgerAccount account =
        LedgerAccount.builder()
            .name("TEST_ACCOUNT")
            .purpose(SYSTEM_ACCOUNT)
            .accountType(LIABILITY)
            .assetType(EUR)
            .build();
    account = ledgerAccountRepository.save(account);

    Instant now = Instant.now();
    Instant yesterday = now.minus(1, DAYS);
    Instant twoDaysAgo = now.minus(2, DAYS);
    Instant tomorrow = now.plus(1, DAYS);

    // Create transactions at different dates
    LedgerTransaction tx1 =
        LedgerTransaction.builder()
            .transactionDate(twoDaysAgo)
            .metadata(Map.of("test", "tx1"))
            .build();
    tx1.addEntry(account, new BigDecimal("-100.00")); // Add 100

    LedgerTransaction tx2 =
        LedgerTransaction.builder()
            .transactionDate(yesterday)
            .metadata(Map.of("test", "tx2"))
            .build();
    tx2.addEntry(account, new BigDecimal("-50.00")); // Add 50

    LedgerTransaction tx3 =
        LedgerTransaction.builder()
            .transactionDate(tomorrow)
            .metadata(Map.of("test", "tx3"))
            .build();
    tx3.addEntry(account, new BigDecimal("-25.00")); // Add 25

    // When & Then
    // Balance at two days ago should only include tx1
    assertThat(account.getBalanceAt(twoDaysAgo)).isEqualTo(new BigDecimal("-100.00"));

    // Balance at yesterday should include tx1 and tx2
    assertThat(account.getBalanceAt(yesterday)).isEqualTo(new BigDecimal("-150.00"));

    // Balance at now should include tx1 and tx2 (not future tx3)
    assertThat(account.getBalanceAt(now)).isEqualTo(new BigDecimal("-150.00"));

    // Balance at tomorrow should include all transactions
    assertThat(account.getBalanceAt(tomorrow)).isEqualTo(new BigDecimal("-175.00"));

    // Total balance should include all transactions
    assertThat(account.getBalance()).isEqualTo(new BigDecimal("-175.00"));
  }

  @Test
  @DisplayName("should calculate balance between dates")
  public void shouldCalculateBalanceBetweenDates() {
    // Given: Create an account and add transactions at different dates
    LedgerAccount account =
        LedgerAccount.builder()
            .name("TEST_ACCOUNT")
            .purpose(SYSTEM_ACCOUNT)
            .accountType(LIABILITY)
            .assetType(EUR)
            .build();
    account = ledgerAccountRepository.save(account);

    Instant now = Instant.now();
    Instant yesterday = now.minus(1, DAYS);
    Instant twoDaysAgo = now.minus(2, DAYS);
    Instant threeDaysAgo = now.minus(3, DAYS);
    Instant tomorrow = now.plus(1, DAYS);

    // Create transactions at different dates
    LedgerTransaction tx1 =
        LedgerTransaction.builder()
            .transactionDate(threeDaysAgo)
            .metadata(Map.of("test", "tx1"))
            .build();
    tx1.addEntry(account, new BigDecimal("-100.00"));

    LedgerTransaction tx2 =
        LedgerTransaction.builder()
            .transactionDate(twoDaysAgo)
            .metadata(Map.of("test", "tx2"))
            .build();
    tx2.addEntry(account, new BigDecimal("-50.00"));

    LedgerTransaction tx3 =
        LedgerTransaction.builder()
            .transactionDate(yesterday)
            .metadata(Map.of("test", "tx3"))
            .build();
    tx3.addEntry(account, new BigDecimal("-25.00"));

    LedgerTransaction tx4 =
        LedgerTransaction.builder()
            .transactionDate(tomorrow)
            .metadata(Map.of("test", "tx4"))
            .build();
    tx4.addEntry(account, new BigDecimal("-10.00"));

    // When & Then
    // Balance between three days ago and two days ago should include tx1 and tx2
    assertThat(account.getBalanceBetween(threeDaysAgo, twoDaysAgo))
        .isEqualTo(new BigDecimal("-150.00"));

    // Balance between two days ago and yesterday should include tx2 and tx3
    assertThat(account.getBalanceBetween(twoDaysAgo, yesterday))
        .isEqualTo(new BigDecimal("-75.00"));

    // Balance between yesterday and tomorrow should include tx3 and tx4
    assertThat(account.getBalanceBetween(yesterday, tomorrow)).isEqualTo(new BigDecimal("-35.00"));

    // Balance for a single day (yesterday) should only include tx3
    assertThat(account.getBalanceBetween(yesterday, yesterday)).isEqualTo(new BigDecimal("-25.00"));

    // Balance for entire range should include all transactions
    assertThat(account.getBalanceBetween(threeDaysAgo, tomorrow))
        .isEqualTo(new BigDecimal("-185.00"));
  }

  @Test
  @DisplayName("should return zero for empty account balance queries")
  public void shouldReturnZeroForEmptyAccountBalanceQueries() {
    // Given: An account with no entries
    LedgerAccount account =
        LedgerAccount.builder()
            .name("EMPTY_ACCOUNT")
            .purpose(SYSTEM_ACCOUNT)
            .accountType(LIABILITY)
            .assetType(EUR)
            .build();
    account = ledgerAccountRepository.save(account);

    Instant now = Instant.now();
    Instant yesterday = now.minus(1, DAYS);

    // When & Then
    assertThat(account.getBalance()).isEqualTo(ZERO);
    assertThat(account.getBalanceAt(now)).isEqualTo(ZERO);
    assertThat(account.getBalanceBetween(yesterday, now)).isEqualTo(ZERO);
  }
}
