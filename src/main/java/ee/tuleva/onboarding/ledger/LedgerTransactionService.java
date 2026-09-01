package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
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

  @Transactional
  public LedgerTransaction createTransaction(
      TransactionType transactionType,
      Instant transactionDate,
      @Nullable UUID externalReference,
      Map<String, Object> metadata,
      LedgerEntryDto... ledgerEntryDtos) {
    var transaction =
        LedgerTransaction.builder()
            .transactionType(transactionType)
            .transactionDate(transactionDate)
            .externalReference(externalReference)
            .metadata(metadata)
            .build();

    for (LedgerEntryDto ledgerEntryDto : ledgerEntryDtos) {
      transaction.addEntry(ledgerEntryDto.account, ledgerEntryDto.amount);
    }

    return ledgerTransactionRepository.saveAndFlush(transaction);
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

  public record LedgerEntryDto(LedgerAccount account, BigDecimal amount) {}
}
