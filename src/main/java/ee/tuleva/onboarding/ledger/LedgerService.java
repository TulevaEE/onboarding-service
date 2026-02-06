package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.User;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;

  public LedgerAccount getUserAccount(User user, UserAccount userAccount) {
    LedgerParty userParty = getOrCreateParty(user);
    return getOrCreateUserAccount(userParty, userAccount);
  }

  public LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return getOrCreate(
        () -> ledgerAccountService.findSystemAccount(systemAccount),
        () -> ledgerAccountService.createSystemAccount(systemAccount),
        "System account not found after creation: systemAccount=" + systemAccount.name());
  }

  private LedgerParty getOrCreateParty(Person person) {
    return getOrCreate(
        () -> ledgerPartyService.getParty(person),
        () -> ledgerPartyService.createParty(person),
        "Party not found after creation: personalCode=" + person.getPersonalCode());
  }

  private LedgerAccount getOrCreateUserAccount(LedgerParty owner, UserAccount userAccount) {
    return getOrCreate(
        () -> ledgerAccountService.findUserAccount(owner, userAccount),
        () -> ledgerAccountService.createUserAccount(owner, userAccount),
        "Account not found after creation: userAccount=" + userAccount.name());
  }

  private <T> T getOrCreate(Supplier<Optional<T>> finder, Runnable creator, String errorMessage) {
    return finder
        .get()
        .orElseGet(
            () -> {
              try {
                creator.run();
              } catch (DataIntegrityViolationException ignored) {
              }
              return finder.get().orElseThrow(() -> new IllegalStateException(errorMessage));
            });
  }
}
