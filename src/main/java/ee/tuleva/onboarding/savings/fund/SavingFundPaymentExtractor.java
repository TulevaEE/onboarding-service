package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import ee.tuleva.onboarding.swedbank.statement.BankStatementAccount;
import ee.tuleva.onboarding.swedbank.statement.BankStatementEntry;
import ee.tuleva.onboarding.swedbank.statement.TransactionType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SavingFundPaymentExtractor {

  public List<SavingFundPayment> extractPayments(BankStatement statement, Instant receivedAt) {
    log.debug("Extracting payments from bank statement");

    try {
      return extractPaymentsFromStatement(statement, receivedAt);
    } catch (PaymentProcessingException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to extract payments from bank statement: {}", e.getMessage(), e);
      throw new PaymentProcessingException("Failed to extract payments from bank statement", e);
    }
  }

  private List<SavingFundPayment> extractPaymentsFromStatement(
      BankStatement statement, Instant receivedAt) {
    var account = statement.getBankStatementAccount();

    return statement.getEntries().stream()
        .map(entry -> convertToSavingFundPayment(entry, account, receivedAt))
        .toList();
  }

  private SavingFundPayment convertToSavingFundPayment(
      BankStatementEntry entry, BankStatementAccount account, Instant receivedAt) {

    if (!Objects.equals(entry.getCurrencyCode(), "EUR")) {
      throw new PaymentProcessingException(
          "Bank transfer currency not supported: " + entry.getCurrencyCode());
    }

    var currency = Currency.EUR;

    var counterParty = entry.getDetails();

    // For CREDIT: counterparty is remitter, account holder is beneficiary
    // For DEBIT: account holder is remitter, counterparty is beneficiary
    if (entry.getTransactionType() == TransactionType.CREDIT) {
      return SavingFundPayment.builder()
          .amount(entry.getAmount())
          .currency(currency)
          .remitterIban(counterParty.getIban())
          .remitterIdCode(counterParty.getPersonalCode().orElse(null))
          .remitterName(counterParty.getName())
          .beneficiaryIban(account.iban())
          .beneficiaryIdCode(account.accountHolderIdCode())
          .beneficiaryName(account.accountHolderName())
          .description(entry.getRemittanceInformation())
          .externalId(entry.getExternalId())
          .receivedAt(receivedAt)
          .build();
    } else { // DEBIT
      return SavingFundPayment.builder()
          .amount(entry.getAmount()) // Already negative for debits
          .currency(currency)
          .remitterIban(account.iban())
          .remitterIdCode(account.accountHolderIdCode())
          .remitterName(account.accountHolderName())
          .beneficiaryIban(counterParty.getIban())
          .beneficiaryIdCode(counterParty.getPersonalCode().orElse(null))
          .beneficiaryName(counterParty.getName())
          .description(entry.getRemittanceInformation())
          .externalId(entry.getExternalId())
          .receivedAt(receivedAt)
          .build();
    }
  }
}
