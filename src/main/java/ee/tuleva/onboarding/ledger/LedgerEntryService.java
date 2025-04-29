package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("dev")
@Service
@RequiredArgsConstructor
class LedgerEntryService {

  private final LedgerEntryRepository ledgerEntryRepository;

  LedgerEntry createEntry(
      LedgerAccount ledgerAccount, LedgerTransaction ledgerTransaction, BigDecimal amount) {
    var entry =
        LedgerEntry.builder()
            .account(ledgerAccount)
            .transaction(ledgerTransaction)
            .amount(amount) // TODO CLAMP ACCORDING TO ledger.asset_type PRECISION
            .build();

    return ledgerEntryRepository.save(entry);
  }
}
