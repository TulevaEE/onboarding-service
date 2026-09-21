package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserUnitBalanceGuard {
  private final LedgerAccountRepository ledgerAccountRepository;

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
      if (isNotATake(delta)) {
        continue;
      }

      var balance = ledgerAccountRepository.balanceOf(account);

      if (takesMoreUnitsThanHeld(balance, delta)) {
        var held = balance.negate();
        if (enforce && transactionType != ADJUSTMENT) {
          throw new UnitBalanceViolationException(account.getName(), held, delta);
        }
        log.error(
            "Unit balance invariant violated ({}): account={}, transactionType={}, held={}, requested={}",
            transactionType == ADJUSTMENT
                ? "admin adjustment, allowed through"
                : "not enforced yet",
            account.getName(),
            transactionType,
            held,
            delta);
      }
    }
  }

  private static boolean isNotATake(BigDecimal delta) {
    return delta.signum() <= 0;
  }

  private static boolean takesMoreUnitsThanHeld(BigDecimal balance, BigDecimal delta) {
    return balance.add(delta).compareTo(ZERO) > 0;
  }

  private void lockInDeterministicOrder(Collection<LedgerAccount> accounts) {
    accounts.stream()
        .sorted(comparing(account -> requireNonNull(account.getId())))
        .forEach(account -> ledgerAccountRepository.lockAccount(requireNonNull(account.getId())));
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
