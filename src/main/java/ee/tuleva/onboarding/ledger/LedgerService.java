package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;

  public LedgerAccount getUserAccount(User user, UserAccount userAccount) {
    LedgerParty userParty =
        ledgerPartyService.getParty(user).orElseGet(() -> ledgerPartyService.createParty(user));

    return ledgerAccountService
        .findUserAccount(userParty, userAccount)
        .orElseGet(() -> ledgerAccountService.createUserAccount(userParty, userAccount));
  }

  public LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerAccountService
        .findSystemAccount(systemAccount)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount));
  }
}
