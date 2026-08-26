package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * You cannot take fund units out of an account that does not hold them.
 *
 * <p>The ledger validated that a transaction's entries balance against each other, never that an
 * account had what was being taken from it. So a redemption drawing more units than were ever
 * reserved posted cleanly and pushed the reserved account past zero, silently.
 *
 * <p><b>The rule is "never positive", not "never negative."</b> The user unit accounts are
 * liabilities, so holding units means a negative balance — {@code
 * RedemptionService.getEffectiveAvailableFundUnits} reads the balance and negates it. Reserving
 * moves units out of {@code FUND_UNITS} (a positive delta there) and into {@code
 * FUND_UNITS_RESERVED}; redeeming and cancelling draw the reserved account back towards zero.
 * Drawing more than is held therefore pushes the balance <em>above</em> zero.
 *
 * <p>Scoped deliberately to the two user unit accounts, where the invariant is unconditional.
 * Deriving the direction from {@code AccountType} would be tempting and wrong: system accounts
 * legitimately swing the other way in the middle of a flow.
 *
 * <p>Checked on the entries about to be posted, <em>before</em> the transaction is built. Querying
 * the balance in the middle of a half-built object graph makes Hibernate auto-flush entries whose
 * transaction does not exist yet, which corrupts the very posting it is meant to protect. Running
 * before the build is also what makes the read correct for a caller that posts several transactions
 * in one unit of work: the earlier ones are complete by then, and the JPQL sum auto-flushes them,
 * so each posting sees the ones before it.
 *
 * <p><b>Serialising is the point, not a bonus.</b> Without the account row lock this would be a
 * read-then-write check, and the races it has to stop are exactly the concurrent ones: two
 * redemption requests from the same party (two devices, a double click) both read the same
 * available balance and both reserve, and a cancellation interleaving with the batch pricing the
 * same request. Neither goes through {@code RedemptionBatchJob}'s {@code @SchedulerLock}. Locks are
 * taken in account-id order so two transactions touching both unit accounts cannot deadlock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserUnitBalanceGuard {

  private final LedgerAccountRepository ledgerAccountRepository;

  /**
   * Off until the check has been watched against production. Switching straight to enforcement
   * would turn a silent historical violation into a hard failure on unrelated writes.
   */
  @Value("${ledger.unit-balance-guard.enforce:false}")
  private boolean enforce;

  public void check(
      TransactionType transactionType, LedgerTransactionService.LedgerEntryDto... entries) {
    var deltaByAccount = guardedDeltas(entries);
    if (deltaByAccount.isEmpty()) {
      return;
    }

    lockInDeterministicOrder(deltaByAccount.keySet());

    for (var entry : deltaByAccount.entrySet()) {
      var account = entry.getKey();
      var delta = entry.getValue();
      if (delta.signum() <= 0) {
        continue; // adding units to an account can never take units it does not have
      }

      var balance = ledgerAccountRepository.balanceOf(account);
      var resulting = balance.add(delta);

      if (resulting.compareTo(ZERO) > 0) {
        var held = balance.negate();
        // Admin adjustments are the tool we would reach for to remediate an account that already
        // sits above zero, and a correction can legitimately move units into an account still in
        // breach. Blocking those would leave a broken account with no way to fix it, so
        // POST /admin/adjustments logs and proceeds; it is admin-authenticated and audited.
        if (enforce && transactionType != ADJUSTMENT) {
          throw new UnitBalanceViolationException(account.getName(), held, delta);
        }
        log.error(
            "Unit balance invariant violated ({}): account={}, transactionType={}, held={}, requested={}",
            enforce ? "admin adjustment, allowed through" : "not enforced yet",
            account.getName(),
            transactionType,
            held,
            delta);
      }
    }
  }

  private void lockInDeterministicOrder(Collection<LedgerAccount> accounts) {
    accounts.stream()
        .sorted(comparing(LedgerAccount::getId))
        .forEach(account -> ledgerAccountRepository.lockAccount(account.getId()));
  }

  private Map<LedgerAccount, BigDecimal> guardedDeltas(
      LedgerTransactionService.LedgerEntryDto... entries) {
    Map<LedgerAccount, BigDecimal> deltas = new LinkedHashMap<>();
    for (var entry : entries) {
      if (entry.account() == null || !isGuarded(entry.account())) {
        continue;
      }
      deltas.merge(entry.account(), entry.amount(), BigDecimal::add);
    }
    return deltas;
  }

  private static boolean isGuarded(LedgerAccount account) {
    return account.getAssetType() == FUND_UNIT && account.isUserAccount();
  }
}
