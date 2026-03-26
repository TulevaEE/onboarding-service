package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.role.RoleType;
import org.junit.jupiter.api.Test;

class LedgerPartyTest {

  @Test
  void partyTypeFromRoleType() {
    assertThat(PartyType.from(RoleType.PERSON)).isEqualTo(PartyType.PERSON);
    assertThat(PartyType.from(RoleType.LEGAL_ENTITY)).isEqualTo(PartyType.LEGAL_ENTITY);
  }
}
