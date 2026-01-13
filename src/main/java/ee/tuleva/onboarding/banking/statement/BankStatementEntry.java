package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.iso20022.camt052.CreditDebitCode.CRDT;

import ee.tuleva.onboarding.banking.iso20022.camt052.GenericPersonIdentification1;
import ee.tuleva.onboarding.banking.iso20022.camt052.Party6Choice;
import ee.tuleva.onboarding.banking.iso20022.camt052.ReportEntry2;
import ee.tuleva.onboarding.banking.iso20022.camt053.CreditDebitCode;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public record BankStatementEntry(
    CounterPartyDetails details,
    BigDecimal amount,
    String currencyCode,
    TransactionType transactionType,
    String remittanceInformation,
    String externalId,
    @Nullable String endToEndId) {

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

      var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
      var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
      var relatedParties = transactionDetails.getRltdPties();

      // TODO entry group can break this?
      // if credit, get debitor; otherwise get creditor
      var otherParty = creditOrDebit == CRDT ? relatedParties.getDbtr() : relatedParties.getCdtr();

      var name = otherParty.getNm();
      var personalIdCodes =
          Optional.ofNullable(otherParty.getId())
              .map(Party6Choice::getPrvtId)
              .map(
                  prvtId ->
                      prvtId.getOthr().stream()
                          .map(GenericPersonIdentification1::getId)
                          .filter(id -> id != null && !id.isBlank())
                          .toList())
              .orElseGet(List::of);
      var personalIdCode = Require.atMostOne(personalIdCodes, "personal ID code");

      var otherPartyAccount =
          creditOrDebit == CRDT ? relatedParties.getDbtrAcct() : relatedParties.getCdtrAcct();

      var iban = Require.notNullOrBlank(otherPartyAccount.getId().getIBAN(), "counter-party IBAN");

      return new CounterPartyDetails(name, iban, personalIdCode);
    }

    static CounterPartyDetails from(
        ee.tuleva.onboarding.banking.iso20022.camt053.ReportEntry2 entry) {
      var creditOrDebit = entry.getCdtDbtInd();

      var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
      var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
      var relatedParties = transactionDetails.getRltdPties();

      // TODO entry group can break this?
      // if credit, get debitor; otherwise get creditor
      var otherParty =
          creditOrDebit == CreditDebitCode.CRDT
              ? relatedParties.getDbtr()
              : relatedParties.getCdtr();

      var name = otherParty.getNm();
      var personalIdCodes =
          Optional.ofNullable(otherParty.getId())
              .map(ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice::getPrvtId)
              .map(
                  prvtId ->
                      prvtId.getOthr().stream()
                          .map(
                              ee.tuleva.onboarding.banking.iso20022.camt053
                                      .GenericPersonIdentification1
                                  ::getId)
                          .filter(id -> id != null && !id.isBlank())
                          .toList())
              .orElseGet(List::of);
      var personalIdCode = Require.atMostOne(personalIdCodes, "personal ID code");

      var otherPartyAccount =
          creditOrDebit == CreditDebitCode.CRDT
              ? relatedParties.getDbtrAcct()
              : relatedParties.getCdtrAcct();

      var iban = Require.notNullOrBlank(otherPartyAccount.getId().getIBAN(), "counter-party IBAN");

      return new CounterPartyDetails(name, iban, personalIdCode);
    }
  }

  static BankStatementEntry from(ee.tuleva.onboarding.banking.iso20022.camt053.ReportEntry2 entry) {
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

    // Extract end-to-end ID from transaction references (used for matching return payments)
    var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
    var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
    var endToEndId =
        Optional.ofNullable(transactionDetails.getRefs())
            .map(
                ee.tuleva.onboarding.banking.iso20022.camt053.TransactionReferences2::getEndToEndId)
            .orElse(null);

    return new BankStatementEntry(
        counterPartyDetails,
        entryAmount,
        currencyCode,
        transactionType,
        remittanceInformation,
        externalId,
        endToEndId);
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

    // Extract end-to-end ID from transaction references (used for matching return payments)
    var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
    var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
    var endToEndId =
        Optional.ofNullable(transactionDetails.getRefs())
            .map(
                ee.tuleva.onboarding.banking.iso20022.camt052.TransactionReferences2::getEndToEndId)
            .orElse(null);

    return new BankStatementEntry(
        counterPartyDetails,
        entryAmount,
        currencyCode,
        transactionType,
        remittanceInformation,
        externalId,
        endToEndId);
  }
}
