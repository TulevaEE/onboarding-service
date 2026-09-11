package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.OWN_ACCOUNT_TRANSFER;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.DESCRIPTION;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InternalTransferLedger {

  private final SavingsFundLedgerAccounts accounts;
  private final LedgerTransactionService ledgerTransactionService;

  public LedgerTransaction recordInternalTransfer(
      SystemAccount fromAccount,
      SystemAccount toAccount,
      BigDecimal amount,
      UUID externalReference,
      LocalDate bookingDate,
      String description) {
    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.getKey(),
            OWN_ACCOUNT_TRANSFER.name(),
            DESCRIPTION.getKey(),
            description);

    return ledgerTransactionService.createTransaction(
        OWN_ACCOUNT_TRANSFER,
        accounts.transactionDate(bookingDate),
        externalReference,
        metadata,
        accounts.entry(accounts.getSystemAccount(fromAccount), amount.negate()),
        accounts.entry(accounts.getSystemAccount(toAccount), amount));
  }
}
