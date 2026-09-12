package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_PAYOUT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LedgerTransactionService {

  private final LedgerTransactionRepository ledgerTransactionRepository;
  private final UserUnitBalanceGuard userUnitBalanceGuard;

  @Transactional
  public LedgerTransaction createTransaction(
      TransactionType transactionType,
      Instant transactionDate,
      @Nullable UUID externalReference,
      Map<String, Object> metadata,
      LedgerEntryDto... ledgerEntryDtos) {
    userUnitBalanceGuard.check(transactionType, ledgerEntryDtos);

    var transaction =
        LedgerTransaction.builder()
            .transactionType(transactionType)
            .transactionDate(transactionDate)
            .externalReference(externalReference)
            .metadata(metadata)
            .build();

    rejectIfAHolderAccountWouldBeLeftInDebit(ledgerEntryDtos);
    for (LedgerEntryDto ledgerEntryDto : ledgerEntryDtos) {
      transaction.addEntry(ledgerEntryDto.account, ledgerEntryDto.amount);
    }

    return ledgerTransactionRepository.saveAndFlush(transaction);
  }

  private static void rejectIfAHolderAccountWouldBeLeftInDebit(LedgerEntryDto... entries) {
    var projected = new LinkedHashMap<LedgerAccount, BigDecimal>();
    for (LedgerEntryDto entry : entries) {
      if (entry.account().isHolderLiabilityAccount()) {
        projected.merge(entry.account(), entry.amount(), BigDecimal::add);
      }
    }
    projected.forEach(
        (account, delta) -> {
          BigDecimal resultingBalance = account.getBalance().add(delta);
          if (resultingBalance.signum() > 0) {
            throw new IllegalStateException(
                "Transaction would leave a holder account in debit: accountId="
                    + account.getId()
                    + ", resultingBalance="
                    + resultingBalance);
          }
        });
  }

  public boolean existsByExternalReferenceAndTransactionType(
      UUID externalReference, TransactionType transactionType) {
    return ledgerTransactionRepository.existsByExternalReferenceAndTransactionType(
        externalReference, transactionType);
  }

  public boolean existsByExternalReference(UUID externalReference) {
    return ledgerTransactionRepository.existsByExternalReference(externalReference);
  }

  public boolean hasEntriesForAccountName(String accountName) {
    return ledgerTransactionRepository.countEntriesForAccountName(accountName) > 0;
  }

  public long countUnresolvedByTransactionTypeAndAccountName(
      TransactionType transactionType, String accountName) {
    return ledgerTransactionRepository.countUnresolvedByTransactionTypeAndAccountName(
        transactionType, accountName);
  }

  public List<LedgerTransaction> findUnresolvedByTransactionTypeAndAccountName(
      TransactionType transactionType, String accountName) {
    return ledgerTransactionRepository.findUnresolvedByTransactionTypeAndAccountName(
        transactionType, accountName);
  }

  Optional<LedgerTransaction> findByExternalReferenceAndTransactionType(
      UUID externalReference, TransactionType transactionType) {
    return ledgerTransactionRepository.findByExternalReferenceAndTransactionType(
        externalReference, transactionType);
  }

  public List<UUID> findHolderAccountIdsInDebit() {
    return ledgerTransactionRepository.findHolderAccountIdsInDebit(LIABILITY);
  }

  public List<UUID> findPayoutIdsBookedToAnotherPartyThanPriced() {
    return ledgerTransactionRepository.findPayoutIdsBookedToAnotherPartyThanPriced(
        REDEMPTION_PAYOUT, REDEMPTION_REQUEST, ADJUSTMENT);
  }

  public record LedgerEntryDto(LedgerAccount account, BigDecimal amount) {}
}
