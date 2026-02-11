package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.USER;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LedgerPartyService {

  private final LedgerPartyRepository ledgerPartyRepository;

  LedgerParty createParty(Person person) {
    var ledgerParty =
        LedgerParty.builder()
            .partyType(USER)
            .ownerId(person.getPersonalCode())
            .details(Map.of())
            .build();

    return ledgerPartyRepository.save(ledgerParty);
  }

  public Optional<LedgerParty> getParty(Person person) {
    return Optional.ofNullable(
        ledgerPartyRepository.findByOwnerId(person.getPersonalCode())); // TODO party representative
  }

  public Optional<LedgerParty> getParty(String personalCode) {
    return Optional.ofNullable(ledgerPartyRepository.findByOwnerId(personalCode));
  }
}
