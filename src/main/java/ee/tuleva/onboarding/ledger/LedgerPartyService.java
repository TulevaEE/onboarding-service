package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.USER;

import ee.tuleva.onboarding.user.User;

import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("dev")
@Service
@RequiredArgsConstructor
class LedgerPartyService {

    private final LedgerPartyRepository ledgerPartyRepository;

    LedgerParty createPartyForUser(User user) {
        var ledgerParty =
                LedgerParty.builder().type(USER).name(user.getPersonalCode()).details(Map.of()).build();

        return ledgerPartyRepository.save(ledgerParty);
    }

    Optional<LedgerParty> getPartyForUser(User user) {
        return Optional.ofNullable(
                ledgerPartyRepository.findByName(user.getPersonalCode())); // TODO party representative
    }
}
