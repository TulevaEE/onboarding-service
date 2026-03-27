package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LedgerPartyService {

  private final LedgerPartyRepository ledgerPartyRepository;

  LedgerParty createParty(String ownerId, PartyType partyType) {
    var ledgerParty =
        LedgerParty.builder().partyType(partyType).ownerId(ownerId).details(Map.of()).build();

    return ledgerPartyRepository.save(ledgerParty);
  }

  public Optional<LedgerParty> getParty(String ownerId, PartyType partyType) {
    return Optional.ofNullable(ledgerPartyRepository.findByOwnerIdAndPartyType(ownerId, partyType));
  }
}
