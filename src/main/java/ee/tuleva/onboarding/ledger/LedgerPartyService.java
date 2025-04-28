package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.USER;

@Profile("dev")
@Service
@RequiredArgsConstructor
class LedgerPartyService {

  private final LedgerPartyRepository ledgerPartyRepository;

  public LedgerParty createPartyForUser(User user) {
    var ledgerParty = LedgerParty.builder()
        .type(USER)
        .name(user.getPersonalCode())
        .build();

    return ledgerPartyRepository.save(ledgerParty);
  }

  public LedgerParty getPartyForUser(User user) {
    return ledgerPartyRepository.findByName(user.getPersonalCode()); // TODO party representative
  }
}
