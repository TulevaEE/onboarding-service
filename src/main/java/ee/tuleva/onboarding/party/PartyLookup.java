package ee.tuleva.onboarding.party;

import java.util.Optional;
import java.util.function.Function;

public record PartyLookup(PartyId.Type type, Function<String, Optional<? extends Party>> finder) {

  public Optional<Party> find(String code) {
    return finder.apply(code).map(Party.class::cast);
  }
}
