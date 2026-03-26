package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;

  public void initializeUserAccounts(Person person) {
    String ownerId = person.getPersonalCode();
    LedgerParty party =
        ledgerPartyService
            .getParty(ownerId)
            .orElseGet(() -> ledgerPartyService.createParty(ownerId, PERSON));

    for (var userAccount : UserAccount.values()) {
      if (ledgerAccountService.findUserAccount(party, userAccount).isEmpty()) {
        ledgerAccountService.createUserAccount(party, userAccount);
      }
    }
  }

  public LedgerAccount getPartyAccount(
      String ownerId, PartyType partyType, UserAccount userAccount) {
    LedgerParty party =
        ledgerPartyService
            .getParty(ownerId)
            .orElseGet(() -> ledgerPartyService.createParty(ownerId, partyType));
    return ledgerAccountService
        .findUserAccount(party, userAccount)
        .orElseGet(() -> ledgerAccountService.createUserAccount(party, userAccount));
  }

  public int countAccountsWithPositiveBalance(UserAccount userAccount) {
    return ledgerAccountService.countAccountsWithPositiveBalance(userAccount);
  }

  public LedgerAccount getSystemAccount(SystemAccount systemAccount, TulevaFund fund) {
    return ledgerAccountService
        .findSystemAccount(systemAccount, fund)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount, fund));
  }
}
