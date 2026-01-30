package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LedgerTransactionService {

  private final LedgerTransactionRepository ledgerTransactionRepository;

  @Transactional
  public LedgerTransaction createTransaction(
      TransactionType transactionType,
      Instant transactionDate,
      Map<String, Object> metadata,
      LedgerEntryDto... ledgerEntryDtos) {
    return createTransaction(transactionType, transactionDate, null, metadata, ledgerEntryDtos);
  }

  @Transactional
  public LedgerTransaction createTransaction(
      TransactionType transactionType,
      Instant transactionDate,
      UUID externalReference,
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
      transaction.addEntry(
          ledgerEntryDto.account,
          ledgerEntryDto.amount); // TODO CLAMP ACCORDING TO ledger.asset_type PRECISION
    }

    return ledgerTransactionRepository.save(transaction);
  }

  public boolean existsByExternalReference(UUID externalReference) {
    return ledgerTransactionRepository.existsByExternalReference(externalReference);
  }

  public boolean existsByExternalReferenceAndTransactionType(
      UUID externalReference, TransactionType transactionType) {
    return ledgerTransactionRepository.existsByExternalReferenceAndTransactionType(
        externalReference, transactionType);
  }

  public record LedgerEntryDto(LedgerAccount account, BigDecimal amount) {}
}
