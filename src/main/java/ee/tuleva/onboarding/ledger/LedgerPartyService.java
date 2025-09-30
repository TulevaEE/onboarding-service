package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.USER;

import ee.tuleva.onboarding.user.User;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})
@Service
@RequiredArgsConstructor
class LedgerPartyService {

  private final LedgerPartyRepository ledgerPartyRepository;

  LedgerParty createPartyForUser(User user, String name) {
    var ledgerParty =
        LedgerParty.builder()
            .type(USER)
            .ownerId(user.getPersonalCode())
            .name(name)
            .details(Map.of())
            .build();

    return ledgerPartyRepository.save(ledgerParty);
  }

  public Optional<LedgerParty> getPartyForUser(User user) {
    return Optional.ofNullable(
        ledgerPartyRepository.findByOwnerId(user.getPersonalCode())); // TODO party representative
  }
}
