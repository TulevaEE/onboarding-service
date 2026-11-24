package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNCLASSIFIED_BANK_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNDERLYING_FUND_MANAGEMENT_FEE_PAYMENT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.DESCRIPTION;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnderlyingFundFeeMoves {

  private final LedgerTransactionService transactionService;
  private final LedgerService ledgerService;

  public List<BookedManagementFeePayment> findManagementFeePayments(TulevaFund fund) {
    var managementFeeAccount = SystemAccount.MANAGEMENT_FEE.getAccountName(fund);
    return transactionService
        .findReferencedByTransactionTypeAndAccountName(MANAGEMENT_FEE_PAYMENT, managementFeeAccount)
        .stream()
        .map(payment -> booked(payment, managementFeeAccount))
        .sorted(comparing(BookedManagementFeePayment::transactionDate))
        .toList();
  }

  public boolean isMoved(BookedManagementFeePayment payment) {
    return transactionService.existsByExternalReferenceAndTransactionType(
        payment.externalReference(), UNDERLYING_FUND_MANAGEMENT_FEE_PAYMENT);
  }

  @Transactional
  public LedgerTransaction move(TulevaFund fund, BookedManagementFeePayment payment) {
    return transactionService
        .findByExternalReferenceAndTransactionType(
            payment.externalReference(), UNDERLYING_FUND_MANAGEMENT_FEE_PAYMENT)
        .orElseGet(() -> bookMove(fund, payment));
  }

  private LedgerTransaction bookMove(TulevaFund fund, BookedManagementFeePayment payment) {
    var bankDescription =
        requireNonNull(
            payment.bankDescription(),
            "Management fee payment without a bank description: externalReference=%s"
                .formatted(payment.externalReference()));
    return transactionService.createTransaction(
        UNDERLYING_FUND_MANAGEMENT_FEE_PAYMENT,
        payment.transactionDate(),
        payment.externalReference(),
        Map.of(
            OPERATION_TYPE.getKey(),
            UNDERLYING_FUND_MANAGEMENT_FEE_PAYMENT.name(),
            DESCRIPTION.getKey(),
            "Moved from the management fee: " + bankDescription),
        new LedgerEntryDto(
            ledgerService.getSystemAccount(SystemAccount.UNDERLYING_FUND_MANAGEMENT_FEE, fund),
            payment.amount()),
        new LedgerEntryDto(
            ledgerService.getSystemAccount(SystemAccount.MANAGEMENT_FEE, fund),
            payment.amount().negate()));
  }

  private BookedManagementFeePayment booked(LedgerTransaction payment, String accountName) {
    var externalReference = requireNonNull(payment.getExternalReference());
    return new BookedManagementFeePayment(
        externalReference,
        payment.getTransactionDate(),
        amountOn(payment, accountName),
        bankDescription(payment, externalReference));
  }

  private @Nullable String bankDescription(LedgerTransaction payment, UUID externalReference) {
    var bankEntry =
        transactionService
            .findByExternalReferenceAndTransactionType(externalReference, UNCLASSIFIED_BANK_ENTRY)
            .orElse(payment);
    return (String) bankEntry.getMetadata().get(DESCRIPTION.getKey());
  }

  private static BigDecimal amountOn(LedgerTransaction payment, String accountName) {
    return payment.getEntries().stream()
        .filter(entry -> accountName.equals(entry.getAccount().getName()))
        .map(LedgerEntry::getAmount)
        .reduce(ZERO, BigDecimal::add);
  }
}
