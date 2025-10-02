package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.*;

import ee.tuleva.onboarding.ledger.SavingsFundLedger.SystemAccount;
import ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  public List<LedgerAccount> onboard(User user) {
    LedgerParty existingParty = ledgerPartyService.getParty(user).orElse(null);

    if (existingParty != null) {
      throw new IllegalStateException("User already onboarded");
    }

    LedgerParty party = ledgerPartyService.createParty(user);

    // Create main liability accounts
    ledgerAccountService.createUserAccount(party, CASH);
    ledgerAccountService.createUserAccount(party, CASH_RESERVED);
    ledgerAccountService.createUserAccount(party, CASH_REDEMPTION);

    // Create fund unit accounts
    ledgerAccountService.createUserAccount(party, FUND_UNITS);
    ledgerAccountService.createUserAccount(party, FUND_UNITS_RESERVED);

    // Create income/expense accounts for tracking money flows
    ledgerAccountService.createUserAccount(party, SUBSCRIPTIONS);
    ledgerAccountService.createUserAccount(party, REDEMPTIONS);

    return ledgerAccountService.getAccounts(party);
  }

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
