package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(LedgerPartyService.class)
class LedgerPartyServiceTest {

  @Autowired LedgerPartyService ledgerPartyService;
  @Autowired JdbcClient jdbcClient;

  @Test
  void getOrCreate_createsPartyWhenAbsent() {
    LedgerParty party = ledgerPartyService.getOrCreate("10000001", LEGAL_ENTITY);

    assertThat(party.getOwnerId()).isEqualTo("10000001");
    assertThat(party.getPartyType()).isEqualTo(LEGAL_ENTITY);
    assertThat(party.getDetails()).isEmpty();
    assertThat(partyCount("10000001", LEGAL_ENTITY)).isEqualTo(1);
  }

  @Test
  void getOrCreate_returnsExistingPartyWithoutCreatingDuplicate() {
    LedgerParty first = ledgerPartyService.getOrCreate("10000002", LEGAL_ENTITY);
    LedgerParty second = ledgerPartyService.getOrCreate("10000002", LEGAL_ENTITY);

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(partyCount("10000002", LEGAL_ENTITY)).isEqualTo(1);
  }

  @Test
  void insertPartyIfAbsent_isNoOpWhenPartyAlreadyExists() {
    ledgerPartyService.getOrCreate("10000003", LEGAL_ENTITY);

    assertThatCode(() -> ledgerPartyService.insertPartyIfAbsent("10000003", LEGAL_ENTITY))
        .doesNotThrowAnyException();

    assertThat(partyCount("10000003", LEGAL_ENTITY)).isEqualTo(1);
  }

  @Test
  void getOrCreate_separatesPartyTypesForSameOwnerId() {
    LedgerParty person = ledgerPartyService.getOrCreate("10000004", PERSON);
    LedgerParty legalEntity = ledgerPartyService.getOrCreate("10000004", LEGAL_ENTITY);

    assertThat(person.getId()).isNotEqualTo(legalEntity.getId());
    assertThat(partyCount("10000004", PERSON)).isEqualTo(1);
    assertThat(partyCount("10000004", LEGAL_ENTITY)).isEqualTo(1);
  }

  private long partyCount(String ownerId, LedgerParty.PartyType partyType) {
    return jdbcClient
        .sql(
            "SELECT count(*) FROM ledger.party WHERE owner_id = :ownerId"
                + " AND party_type = CAST(:partyType AS ledger.party_type)")
        .param("ownerId", ownerId)
        .param("partyType", partyType.name())
        .query(Long.class)
        .single();
  }
}
