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

    if (!Objects.equals(entry.currencyCode(), "EUR")) {
      throw new PaymentProcessingException(
          "Bank transfer currency not supported: " + entry.currencyCode());
    }

    var currency = Currency.EUR;

    var counterParty = entry.details();

    // For CREDIT: counterparty is remitter, account holder is beneficiary
    // For DEBIT: account holder is remitter, counterparty is beneficiary
    var builder =
        SavingFundPayment.builder()
            .amount(entry.amount())
            .currency(currency)
            .description(entry.remittanceInformation())
            .externalId(entry.externalId())
            .receivedAt(receivedAt);

    if (entry.transactionType() == TransactionType.CREDIT) {
      builder
          .remitterIban(counterParty.getIban())
          .remitterIdCode(counterParty.getPersonalCode().orElse(null))
          .remitterName(counterParty.getName())
          .beneficiaryIban(account.iban())
          .beneficiaryIdCode(account.accountHolderIdCode())
          .beneficiaryName(account.accountHolderName());
    } else { // DEBIT
      builder
          .remitterIban(account.iban())
          .remitterIdCode(account.accountHolderIdCode())
          .remitterName(account.accountHolderName())
          .beneficiaryIban(counterParty.getIban())
          .beneficiaryIdCode(counterParty.getPersonalCode().orElse(null))
          .beneficiaryName(counterParty.getName());
    }

    return builder.build();
  }
}
