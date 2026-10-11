package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EQUITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.OFF_BALANCE;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({LedgerAccountService.class, LedgerPartyService.class})
class LedgerAccountServiceIntegrationTest {

  private static final String SYSTEM_ACCOUNT_NAME = "TEST_SYSTEM_ACCOUNT";

  @Autowired LedgerAccountService ledgerAccountService;
  @Autowired LedgerPartyService ledgerPartyService;
  @Autowired JdbcClient jdbcClient;
  @Autowired EntityManager entityManager;

  @Test
  void createUserAccount_createsAccountWhenAbsent() {
    LedgerParty party = ledgerPartyService.getOrCreate("20000001", LEGAL_ENTITY);

    LedgerAccount account = ledgerAccountService.createUserAccount(party, SUBSCRIPTIONS);

    assertThat(account.getName()).isEqualTo(SUBSCRIPTIONS.name());
    assertThat(account.getOwner().getId()).isEqualTo(party.getId());
    assertThat(accountCount(party, SUBSCRIPTIONS)).isEqualTo(1);
  }

  @Test
  void createUserAccount_returnsExistingAccountWithoutCreatingDuplicate() {
    LedgerParty party = ledgerPartyService.getOrCreate("20000002", LEGAL_ENTITY);

    LedgerAccount first = ledgerAccountService.createUserAccount(party, SUBSCRIPTIONS);
    LedgerAccount second = ledgerAccountService.createUserAccount(party, SUBSCRIPTIONS);

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(accountCount(party, SUBSCRIPTIONS)).isEqualTo(1);
  }

  @Test
  void insertUserAccountIfAbsent_isNoOpWhenAccountAlreadyExists() {
    LedgerParty party = ledgerPartyService.getOrCreate("20000003", LEGAL_ENTITY);
    ledgerAccountService.createUserAccount(party, SUBSCRIPTIONS);

    assertThatCode(() -> ledgerAccountService.insertUserAccountIfAbsent(party, SUBSCRIPTIONS))
        .doesNotThrowAnyException();

    assertThat(accountCount(party, SUBSCRIPTIONS)).isEqualTo(1);
  }

  @Test
  void createSystemAccount_returnsExistingAccountWithoutCreatingDuplicate() {
    LedgerAccount first = ledgerAccountService.createSystemAccount(SYSTEM_ACCOUNT_NAME, ASSET, EUR);
    LedgerAccount second =
        ledgerAccountService.createSystemAccount(SYSTEM_ACCOUNT_NAME, ASSET, EUR);

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(systemAccountCount(SYSTEM_ACCOUNT_NAME)).isEqualTo(1);
    assertThat(ledgerAccountService.findSystemAccountByName(SYSTEM_ACCOUNT_NAME, ASSET, EUR))
        .map(LedgerAccount::getId)
        .contains(first.getId());
  }

  @Test
  void newAccountTypesRoundTrip() {
    var equity = ledgerAccountService.createSystemAccount("TEST_EQUITY_ACCOUNT", EQUITY, EUR);
    var offBalance =
        ledgerAccountService.createSystemAccount("TEST_OFF_BALANCE_ACCOUNT", OFF_BALANCE, EUR);
    entityManager.clear();

    assertThat(ledgerAccountService.findSystemAccountByName("TEST_EQUITY_ACCOUNT", EQUITY, EUR))
        .map(LedgerAccount::getId)
        .contains(equity.getId());
    assertThat(
            ledgerAccountService.findSystemAccountByName(
                "TEST_OFF_BALANCE_ACCOUNT", OFF_BALANCE, EUR))
        .map(LedgerAccount::getAccountType)
        .contains(OFF_BALANCE);
    assertThat(offBalance.getAccountType()).isEqualTo(OFF_BALANCE);
  }

  @Test
  void accountMetadataRoundTripsOnBothDatabases() {
    Map<String, Object> metadata =
        Map.of("name", "Bank", "class", 0, "cashFlowCodes", List.of("A10", "B20"));
    var account = ledgerAccountService.createSystemAccount("TEST_DESCRIBED_ACCOUNT", ASSET, EUR);

    account.updateMetadata(metadata);
    entityManager.flush();
    entityManager.clear();

    assertThat(ledgerAccountService.findSystemAccountByName("TEST_DESCRIBED_ACCOUNT", ASSET, EUR))
        .map(LedgerAccount::getMetadata)
        .contains(metadata);
  }

  @Test
  void subledgerAccountsKeepNullMetadata() {
    LedgerParty party = ledgerPartyService.getOrCreate("20000004", LEGAL_ENTITY);
    ledgerAccountService.createUserAccount(party, SUBSCRIPTIONS);
    ledgerAccountService.createSystemAccount(SYSTEM_ACCOUNT_NAME, ASSET, EUR);
    entityManager.clear();

    assertThat(ledgerAccountService.findUserAccount(party, SUBSCRIPTIONS))
        .get()
        .extracting(LedgerAccount::getMetadata)
        .isNull();
    assertThat(ledgerAccountService.findSystemAccountByName(SYSTEM_ACCOUNT_NAME, ASSET, EUR))
        .get()
        .extracting(LedgerAccount::getMetadata)
        .isNull();
  }

  private long accountCount(LedgerParty owner, UserAccount userAccount) {
    return jdbcClient
        .sql(
            "SELECT count(*) FROM ledger.account"
                + " WHERE owner_party_id = :ownerPartyId AND name = :name")
        .param("ownerPartyId", owner.getId())
        .param("name", userAccount.name())
        .query(Long.class)
        .single();
  }

  private long systemAccountCount(String name) {
    return jdbcClient
        .sql("SELECT count(*) FROM ledger.account WHERE name = :name AND owner_party_id IS NULL")
        .param("name", name)
        .query(Long.class)
        .single();
  }
}
