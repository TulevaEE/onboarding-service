package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})
@Service
@RequiredArgsConstructor
class LedgerTransactionService {
  private final Clock clock;

  private final LedgerTransactionRepository ledgerTransactionRepository;

  @Transactional
  public LedgerTransaction createTransaction(
      TransactionType type, Map<String, Object> metadata, LedgerEntryDto... ledgerEntryDtos) {
    var transaction =
        LedgerTransaction.builder()
            .description("") // TODO remove this field
            .transactionType(type)
            .transactionDate(clock.instant())
            .metadata(metadata)
            // .eventLogId(1) // TODO event log ID
            .build();

    for (LedgerEntryDto ledgerEntryDto : ledgerEntryDtos) {
      transaction.addEntry(
          ledgerEntryDto.account,
          ledgerEntryDto.amount); // TODO CLAMP ACCORDING TO ledger.asset_type PRECISION
    }

    return ledgerTransactionRepository.save(transaction);
  }

  public record LedgerEntryDto(LedgerAccount account, BigDecimal amount) {}
}
