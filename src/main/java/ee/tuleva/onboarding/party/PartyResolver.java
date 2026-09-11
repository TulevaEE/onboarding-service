package ee.tuleva.onboarding.party;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PartyResolver {

  private final List<PartyLookup> lookups;

  public Optional<Party> resolve(PartyId partyId) {
    return lookups.stream()
        .filter(lookup -> lookup.type() == partyId.type())
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("No party lookup for type: type=" + partyId.type()))
        .find(partyId.code());
  }
}
