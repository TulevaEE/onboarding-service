package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PartyResolverTest {

  record FakeParty(String code, String name) implements Party {}

  Party person = new FakeParty("38888888888", "Person");
  Party company = new FakeParty("12345678", "Company");
  PartyResolver resolver =
      new PartyResolver(
          List.of(
              new PartyLookup(PERSON, code -> filter(person, code)),
              new PartyLookup(LEGAL_ENTITY, code -> filter(company, code))));

  private static Optional<Party> filter(Party party, String code) {
    return Optional.of(party).filter(match -> match.code().equals(code));
  }

  @Test
  void resolvesPersonThroughThePersonLookup() {
    assertThat(resolver.resolve(new PartyId(PERSON, "38888888888"))).contains(person);
  }

  @Test
  void resolvesLegalEntityThroughTheLegalEntityLookup() {
    assertThat(resolver.resolve(new PartyId(LEGAL_ENTITY, "12345678"))).contains(company);
  }

  @Test
  void returnsEmptyWhenTheLookupFindsNothing() {
    assertThat(resolver.resolve(new PartyId(PERSON, "unknown"))).isEmpty();
  }

  @Test
  void throwsWhenNoLookupSupportsTheType() {
    PartyResolver miswired =
        new PartyResolver(List.of(new PartyLookup(PERSON, code -> Optional.of(person))));

    assertThatThrownBy(() -> miswired.resolve(new PartyId(LEGAL_ENTITY, "12345678")))
        .isInstanceOf(IllegalStateException.class);
  }
}
