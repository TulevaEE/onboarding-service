package ee.tuleva.onboarding.savings.fund;

import ee.swedbank.gateway.iso.response.report.Document;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import ee.tuleva.onboarding.swedbank.statement.BankStatementEntry;
import ee.tuleva.onboarding.swedbank.statement.TransactionType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SavingFundPaymentExtractor {

  public List<SavingFundPayment> extractPayments(Document document, Instant receivedAt) {
    log.debug("Extracting payments from document ");

    try {
      var reports = document.getBkToCstmrAcctRpt().getRpt();

      return reports.stream()
          .map(BankStatement::from)
          .flatMap(statement -> extractPaymentsFromStatement(statement, receivedAt).stream())
          .toList();
    } catch (PaymentProcessingException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to extract payments from document: {}", e.getMessage(), e);
      throw new PaymentProcessingException("Failed to extract payments from bank statement", e);
    }
  }

  private List<SavingFundPayment> extractPaymentsFromStatement(
      BankStatement statement, Instant receivedAt) {
    var accountType = statement.getBankStatementAccount();

    if (accountType.accountHolderName() == null) {
      throw new PaymentProcessingException("Bank statement account holder name not found");
    }

    if (accountType.accountHolderIdCodes().size() != 1) {
      throw new PaymentProcessingException(
          "Bank statement account holder id is not well determined, ids: "
              + accountType.accountHolderIdCodes());
    }

    var accountHolder =
        new AccountHolder(
            accountType.accountHolderName(),
            accountType.accountHolderIdCodes().getFirst(),
            accountType.iban());

    return statement.getEntries().stream()
        .map(entry -> convertToSavingFundPayment(entry, accountHolder, receivedAt))
        .toList();
  }

  private SavingFundPayment convertToSavingFundPayment(
      BankStatementEntry entry, AccountHolder accountHolder, Instant receivedAt) {

    if (!Objects.equals(entry.currencyCode(), "EUR")) {
      throw new PaymentProcessingException(
          "Bank transfer currency not supported: " + entry.currencyCode());
    }

    var currency = Currency.EUR;

    var counterParty = entry.details();

    // Extract and validate endToEndId
    var endToEndId = extractSingleEndToEndId(entry);

    // Extract and validate remittance information for description
    var description = extractDescription(entry);

    // For CREDIT: counterparty is remitter, account holder is beneficiary
    // For DEBIT: account holder is remitter, counterparty is beneficiary
    if (entry.transactionType() == TransactionType.CREDIT) {
      return SavingFundPayment.builder()
          .amount(entry.amount())
          .currency(currency)
          .remitterIban(counterParty.getIban())
          .remitterIdCode(counterParty.getPersonalCode().orElse(null))
          .remitterName(counterParty.getName())
          .beneficiaryIban(accountHolder.iban())
          .beneficiaryIdCode(accountHolder.idCode)
          .beneficiaryName(accountHolder.name)
          .description(description)
          .endToEndId(endToEndId.orElse(null))
          .externalId(entry.externalId())
          .receivedAt(receivedAt)
          .build();
    } else { // DEBIT
      return SavingFundPayment.builder()
          .amount(entry.amount()) // Already negative for debits
          .currency(currency)
          .remitterIban(accountHolder.iban())
          .remitterIdCode(accountHolder.idCode)
          .remitterName(accountHolder.name)
          .beneficiaryIban(counterParty.getIban())
          .beneficiaryIdCode(counterParty.getPersonalCode().orElse(null))
          .beneficiaryName(counterParty.getName())
          .description(description)
          .endToEndId(endToEndId.orElse(null))
          .externalId(entry.externalId())
          .receivedAt(receivedAt)
          .build();
    }
  }

  private Optional<String> extractSingleEndToEndId(BankStatementEntry entry) {
    var endToEndIds = entry.endToEndIds().stream().distinct().toList();

    if (endToEndIds.size() > 1) {
      throw new PaymentProcessingException("Multiple end-to-end IDs found" + endToEndIds);
    }

    return endToEndIds.stream().findFirst();
  }

  private String extractDescription(BankStatementEntry entry) {
    var remittanceInfoList = entry.remittanceInformation();

    if (remittanceInfoList.isEmpty()) {
      return getDefaultDescription(entry);
    }

    if (remittanceInfoList.size() > 1) {
      log.warn(
          "Multiple remittance information entries found: {}. Concatenating with '; '",
          remittanceInfoList);
      return String.join("; ", remittanceInfoList);
    }

    var remittanceInfo = remittanceInfoList.getFirst();
    return remittanceInfo != null && !remittanceInfo.isBlank()
        ? remittanceInfo
        : getDefaultDescription(entry);
  }

  private String getDefaultDescription(BankStatementEntry entry) {
    var counterPartyName = entry.details().getName();
    return entry.transactionType() == TransactionType.CREDIT
        ? "Bank transfer from " + counterPartyName
        : "Bank transfer to " + counterPartyName;
  }

  private record AccountHolder(String name, String idCode, String iban) {}
}
