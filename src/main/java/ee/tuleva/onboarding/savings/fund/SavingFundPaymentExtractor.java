package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.currency.Currency;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SavingFundPaymentExtractor {

  public List<SavingFundPayment> extractPayments(BankStatement statement) {
    log.debug("Extracting payments from bank statement");

    try {
      return extractPaymentsFromStatement(statement);
    } catch (PaymentProcessingException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to extract payments from bank statement: {}", e.getMessage(), e);
      throw new PaymentProcessingException("Failed to extract payments from bank statement", e);
    }
  }

  private List<SavingFundPayment> extractPaymentsFromStatement(BankStatement statement) {
    var account = statement.getBankStatementAccount();

    return statement.getEntries().stream()
        .filter(entry -> entry.details() != null)
        .map(entry -> convertToSavingFundPayment(entry, account))
        .toList();
  }

  private SavingFundPayment convertToSavingFundPayment(
      BankStatementEntry entry, BankStatementAccount account) {

    if (!Objects.equals(entry.currencyCode(), "EUR")) {
      throw new PaymentProcessingException(
          "Bank transfer currency not supported: " + entry.currencyCode());
    }

    var currency = Currency.EUR;

    // Normalize amount to 2 decimal places for EUR (e.g., 150.100 -> 150.10)
    var normalizedAmount = entry.amount().setScale(2, RoundingMode.DOWN);

    // Ensure we're not losing precision
    if (entry.amount().compareTo(normalizedAmount) != 0) {
      throw new PaymentProcessingException(
          "Amount has more than 2 significant decimal places: " + entry.amount());
    }

    var counterParty = entry.details();

    // For CREDIT: counterparty is remitter, account holder is beneficiary
    // For DEBIT: account holder is remitter, counterparty is beneficiary
    var builder =
        SavingFundPayment.builder()
            .amount(normalizedAmount)
            .currency(currency)
            .description(entry.remittanceInformation())
            .externalId(entry.externalId())
            .endToEndId(entry.endToEndId())
            .receivedBefore(entry.receivedBefore());

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
