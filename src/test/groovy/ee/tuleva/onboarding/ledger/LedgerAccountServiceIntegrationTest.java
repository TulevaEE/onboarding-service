package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({LedgerAccountService.class, LedgerPartyService.class})
class LedgerAccountServiceIntegrationTest {

  @Autowired LedgerAccountService ledgerAccountService;
  @Autowired LedgerPartyService ledgerPartyService;
  @Autowired JdbcClient jdbcClient;

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
}
