package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerCashAmounts {

  private final LedgerTransactionRepository ledgerTransactionRepository;

  public Optional<BigDecimal> cashAmountOf(
      UUID externalReference, TransactionType transactionType) {
    return ledgerTransactionRepository.sumIncreasesOf(externalReference, transactionType, EUR);
  }

  public Optional<BigDecimal> cashAmountOf(
      Collection<UUID> externalReferences, TransactionType transactionType) {
    return externalReferences.isEmpty()
        ? Optional.empty()
        : ledgerTransactionRepository.sumIncreasesOfAll(externalReferences, transactionType, EUR);
  }

  public BigDecimal cashAmountBetween(
      TransactionType transactionType, Instant after, Instant until) {
    return ledgerTransactionRepository.sumIncreasesBetween(transactionType, after, until, EUR);
  }
}
