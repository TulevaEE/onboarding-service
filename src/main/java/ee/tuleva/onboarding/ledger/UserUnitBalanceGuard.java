package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
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
 * posts a negative amount; redeeming and cancelling post it back positive. Over-redeeming therefore
 * pushes the balance <em>above</em> zero.
 *
 * <p>Scoped deliberately to the two user unit accounts, where the invariant is unconditional.
 * Deriving the direction from {@code AccountType} would be tempting and wrong: system accounts
 * legitimately swing the other way in the middle of a flow.
 *
 * <p>Checked on the entries about to be posted, <em>before</em> the transaction is built. Querying
 * the balance in the middle of a half-built object graph makes Hibernate auto-flush entries whose
 * transaction does not exist yet, which corrupts the very posting it is meant to protect.
 *
 * <p><b>This is a read-then-write check and does not serialise concurrent draws.</b> In practice
 * the redemption path is serialised by {@code RedemptionBatchJob}'s {@code @SchedulerLock}; making
 * the guarantee hold for every caller needs a database constraint rather than application code,
 * which is the alternative already named in the issue this came from.
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

  public void check(LedgerTransactionService.LedgerEntryDto... entries) {
    var deltaByAccount = guardedDeltas(entries);

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
        if (enforce) {
          throw new UnitBalanceViolationException(account.getName(), held, delta);
        }
        log.error(
            "Unit balance invariant violated (not enforced yet): account={}, held={}, requested={}",
            account.getName(),
            held,
            delta);
      }
    }
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
