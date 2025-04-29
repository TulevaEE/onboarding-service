package ee.tuleva.onboarding.ledger;


import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;

@Profile("dev")
@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;


  public List<LedgerAccount> onboardUser(User user) {
    LedgerParty existingParty =
        ledgerPartyService
            .getPartyForUser(user)
            .orElse(null);

    if (existingParty != null) {
      throw new IllegalStateException("User already onboarded");
    }


    LedgerParty party = ledgerPartyService.createPartyForUser(user);


    LedgerAccount cashAccount = ledgerAccountService.createAccountForParty(party, "Cash account for " + user.getPersonalCode(), EUR, INCOME);
    LedgerAccount stockAccount = ledgerAccountService.createAccountForParty(party, "Stock account for " + user.getPersonalCode(), UNIT, ASSET);


    return ledgerAccountService.getAccountsByLedgerParty(party);
  }
}
