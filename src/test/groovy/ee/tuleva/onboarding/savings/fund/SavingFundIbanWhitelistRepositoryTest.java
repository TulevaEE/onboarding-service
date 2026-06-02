package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.party.PartyId;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class SavingFundIbanWhitelistRepositoryTest {

  @Autowired SavingFundIbanWhitelistRepository repository;
  @Autowired TestEntityManager entityManager;

  private static final String CODE = "39901019992";
  private static final String IBAN = "EE471000001020145685";

  private SavingFundIbanWhitelist save(PartyId.Type type, String code, String iban) {
    return repository.save(
        SavingFundIbanWhitelist.builder()
            .partyType(type)
            .partyCode(code)
            .iban(iban)
            .comment("verified via bank statement")
            .build());
  }

  @Test
  void existsByPartyTypeAndPartyCodeAndIban_whenWhitelisted_isTrue() {
    save(PERSON, CODE, IBAN);

    assertThat(repository.existsByPartyTypeAndPartyCodeAndIban(PERSON, CODE, IBAN)).isTrue();
  }

  @Test
  void existsByPartyTypeAndPartyCodeAndIban_whenDifferentType_isFalse() {
    save(PERSON, CODE, IBAN);

    assertThat(repository.existsByPartyTypeAndPartyCodeAndIban(LEGAL_ENTITY, CODE, IBAN)).isFalse();
  }

  @Test
  void existsByPartyTypeAndPartyCodeAndIban_whenNotWhitelisted_isFalse() {
    save(PERSON, CODE, IBAN);

    assertThat(
            repository.existsByPartyTypeAndPartyCodeAndIban(PERSON, CODE, "EE000000000000000000"))
        .isFalse();
  }

  @Test
  void findByPartyTypeAndPartyCode_returnsOnlyMatchingParty() {
    var mine = save(PERSON, CODE, IBAN);
    save(PERSON, "48709090311", "EE382200221020145685");

    assertThat(repository.findByPartyTypeAndPartyCode(PERSON, CODE)).containsExactly(mine);
  }

  @Test
  void deleteByPartyTypeAndPartyCodeAndIban_removesEntry() {
    save(PERSON, CODE, IBAN);

    long removed = repository.deleteByPartyTypeAndPartyCodeAndIban(PERSON, CODE, IBAN);

    assertThat(removed).isEqualTo(1);
    assertThat(repository.existsByPartyTypeAndPartyCodeAndIban(PERSON, CODE, IBAN)).isFalse();
  }

  @Test
  void duplicatePartyAndIban_violatesUniqueConstraint() {
    save(PERSON, CODE, IBAN);
    save(PERSON, CODE, IBAN);

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }
}
