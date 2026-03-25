package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LedgerPartyService {

  private final LedgerPartyRepository ledgerPartyRepository;

  LedgerParty createParty(String ownerId) {
    var ledgerParty =
        LedgerParty.builder().partyType(PERSON).ownerId(ownerId).details(Map.of()).build();

    return ledgerPartyRepository.save(ledgerParty);
  }

  public Optional<LedgerParty> getParty(String ownerId) {
    return Optional.ofNullable(ledgerPartyRepository.findByOwnerId(ownerId));
  }
}
