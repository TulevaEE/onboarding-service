package ee.tuleva.onboarding.swedbank.statement;

import static ee.swedbank.gateway.iso.response.report.CreditDebitCode.CRDT;

import ee.swedbank.gateway.iso.response.report.Party6Choice;
import ee.swedbank.gateway.iso.response.report.ReportEntry2;
import ee.swedbank.gateway.iso.response.statement.CreditDebitCode;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BankStatementEntry {
  @Getter private final CounterPartyDetails details;
  @Getter private final BigDecimal amount;
  @Getter private final String currencyCode;
  @Getter private final TransactionType transactionType;
  @Getter private final String remittanceInformation;
  @Getter private final String externalId;

  @RequiredArgsConstructor
  public static final class CounterPartyDetails {
    @Getter private final String name;
    @Getter private final String iban;
    @Nullable private final String personalCode;

    public Optional<String> getPersonalCode() {
      return Optional.ofNullable(personalCode);
    }

    public static CounterPartyDetails from(ReportEntry2 entry) {
      var creditOrDebit = entry.getCdtDbtInd();

      // TODO this can have multiple ones depending on bank logic, test response from Swed only has
      // one
      var transactionDetails = entry.getNtryDtls().getFirst().getTxDtls().getFirst();
      var relatedParties = transactionDetails.getRltdPties();

      // TODO entry group can break this?
      // if credit, get debitor; otherwise get creditor
      var otherParty = creditOrDebit == CRDT ? relatedParties.getDbtr() : relatedParties.getCdtr();

      var name = otherParty.getNm();
      var personalIdCode =
          Optional.ofNullable(otherParty.getId())
              .map(Party6Choice::getPrvtId)
              .flatMap(val -> val.getOthr().stream().filter(dt -> dt.getId() != null).findFirst())
              .map(genericPersonIdentification1 -> genericPersonIdentification1.getId())
              .orElseThrow(() -> new BankStatementParseException("Personal code is required"));

      var otherPartyAccount =
          creditOrDebit == CRDT ? relatedParties.getDbtrAcct() : relatedParties.getCdtrAcct();

      var iban = otherPartyAccount.getId().getIBAN();

      return new CounterPartyDetails(name, iban, personalIdCode);
    }

    static CounterPartyDetails from(ee.swedbank.gateway.iso.response.statement.ReportEntry2 entry) {
      var creditOrDebit = entry.getCdtDbtInd();

      // TODO this can have multiple ones depending on bank logic, test response from Swed only has
      // one
      var transactionDetails = entry.getNtryDtls().getFirst().getTxDtls().getFirst();
      var relatedParties = transactionDetails.getRltdPties();

      // TODO entry group can break this?
      // if credit, get debitor; otherwise get creditor
      var otherParty =
          creditOrDebit == CreditDebitCode.CRDT
              ? relatedParties.getDbtr()
              : relatedParties.getCdtr();

      var name = otherParty.getNm();
      var personalIdCode =
          Optional.ofNullable(otherParty.getId())
              .map(ee.swedbank.gateway.iso.response.statement.Party6Choice::getPrvtId)
              .flatMap(val -> val.getOthr().stream().filter(dt -> dt.getId() != null).findFirst())
              .map(genericPersonIdentification1 -> genericPersonIdentification1.getId())
              .orElseThrow(() -> new BankStatementParseException("Personal code is required"));

      var otherPartyAccount =
          creditOrDebit == CreditDebitCode.CRDT
              ? relatedParties.getDbtrAcct()
              : relatedParties.getCdtrAcct();

      var iban = otherPartyAccount.getId().getIBAN();

      return new CounterPartyDetails(name, iban, personalIdCode);
    }
  }

  static BankStatementEntry from(ee.swedbank.gateway.iso.response.statement.ReportEntry2 entry) {
    var counterPartyDetails = CounterPartyDetails.from(entry);
    var creditOrDebit = entry.getCdtDbtInd();
    var creditDebitCoefficient =
        creditOrDebit == CreditDebitCode.CRDT ? BigDecimal.ONE : new BigDecimal("-1.0");
    var entryAmount = entry.getAmt().getValue().multiply(creditDebitCoefficient);

    var currencyCode = entry.getAmt().getCcy();

    // Determine transaction type
    var transactionType =
        creditOrDebit == CreditDebitCode.CRDT ? TransactionType.CREDIT : TransactionType.DEBIT;

    // Collect all remittance information from all transaction details and require exactly one
    var remittanceInformationList =
        entry.getNtryDtls().stream()
            .flatMap(ntryDtl -> ntryDtl.getTxDtls().stream())
            .flatMap(
                txDtl ->
                    Optional.ofNullable(txDtl.getRmtInf())
                        .map(rmtInf -> rmtInf.getUstrd().stream())
                        .orElse(Stream.empty()))
            .filter(ustrd -> ustrd != null && !ustrd.isBlank())
            .toList();
    var remittanceInformation =
        Require.exactlyOne(remittanceInformationList, "remittance information");

    // Extract external ID from entry reference
    var externalId = entry.getNtryRef();

    return new BankStatementEntry(
        counterPartyDetails,
        entryAmount,
        currencyCode,
        transactionType,
        remittanceInformation,
        externalId);
  }

  static BankStatementEntry from(ReportEntry2 entry) {
    var counterPartyDetails = CounterPartyDetails.from(entry);
    var creditOrDebit = entry.getCdtDbtInd();
    var creditDebitCoefficient = creditOrDebit == CRDT ? BigDecimal.ONE : new BigDecimal("-1.0");
    var entryAmount = entry.getAmt().getValue().multiply(creditDebitCoefficient);

    var currencyCode = entry.getAmt().getCcy();

    // Determine transaction type
    var transactionType = creditOrDebit == CRDT ? TransactionType.CREDIT : TransactionType.DEBIT;

    // Collect all remittance information from all transaction details and require exactly one
    var remittanceInformationList =
        entry.getNtryDtls().stream()
            .flatMap(ntryDtl -> ntryDtl.getTxDtls().stream())
            .flatMap(
                txDtl ->
                    Optional.ofNullable(txDtl.getRmtInf())
                        .map(rmtInf -> rmtInf.getUstrd().stream())
                        .orElse(Stream.empty()))
            .filter(ustrd -> ustrd != null && !ustrd.isBlank())
            .toList();
    var remittanceInformation =
        Require.exactlyOne(remittanceInformationList, "remittance information");

    // Extract external ID from entry reference
    var externalId = entry.getNtryRef();

    return new BankStatementEntry(
        counterPartyDetails,
        entryAmount,
        currencyCode,
        transactionType,
        remittanceInformation,
        externalId);
  }
}
