package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.party.PartyId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;

  public void initializeAccounts(PartyId party) {
    var partyType = PartyType.valueOf(party.type().name());
    LedgerParty ledgerParty = ledgerPartyService.getOrCreate(party.code(), partyType);

    for (var userAccount : UserAccount.values()) {
      if (ledgerAccountService.findUserAccount(ledgerParty, userAccount).isEmpty()) {
        ledgerAccountService.createUserAccount(ledgerParty, userAccount);
      }
    }
  }

  public LedgerAccount getPartyAccount(
      String ownerId, PartyType partyType, UserAccount userAccount) {
    LedgerParty party = ledgerPartyService.getOrCreate(ownerId, partyType);
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
