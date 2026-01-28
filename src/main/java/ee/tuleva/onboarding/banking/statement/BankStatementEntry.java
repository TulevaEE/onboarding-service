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
    @Nullable CounterPartyDetails details,
    BigDecimal amount,
    String currencyCode,
    TransactionType transactionType,
    String remittanceInformation,
    String externalId,
    @Nullable String endToEndId,
    @Nullable String subFamilyCode) {

  @RequiredArgsConstructor
  public static final class CounterPartyDetails {

    @Getter private final String name;
    @Getter private final String iban;
    @Nullable private final String personalCode;

    public Optional<String> getPersonalCode() {
      return Optional.ofNullable(personalCode);
    }

    @Nullable
    public static CounterPartyDetails from(ReportEntry2 entry) {
      var creditOrDebit = entry.getCdtDbtInd();

      var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
      var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
      var relatedParties = transactionDetails.getRltdPties();

      if (relatedParties == null) {
        return null;
      }

      var otherParty = creditOrDebit == CRDT ? relatedParties.getDbtr() : relatedParties.getCdtr();

      if (otherParty == null) {
        return null;
      }

      var otherPartyAccount =
          creditOrDebit == CRDT ? relatedParties.getDbtrAcct() : relatedParties.getCdtrAcct();

      if (otherPartyAccount == null || otherPartyAccount.getId() == null) {
        return null;
      }

      var iban = otherPartyAccount.getId().getIBAN();
      if (iban == null || iban.isBlank()) {
        return null;
      }

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

      return new CounterPartyDetails(name, iban, personalIdCode);
    }

    @Nullable
    static CounterPartyDetails from(
        ee.tuleva.onboarding.banking.iso20022.camt053.ReportEntry2 entry) {
      var creditOrDebit = entry.getCdtDbtInd();

      var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
      var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
      var relatedParties = transactionDetails.getRltdPties();

      if (relatedParties == null) {
        return null;
      }

      var otherParty =
          creditOrDebit == CreditDebitCode.CRDT
              ? relatedParties.getDbtr()
              : relatedParties.getCdtr();

      if (otherParty == null) {
        return null;
      }

      var otherPartyAccount =
          creditOrDebit == CreditDebitCode.CRDT
              ? relatedParties.getDbtrAcct()
              : relatedParties.getCdtrAcct();

      if (otherPartyAccount == null || otherPartyAccount.getId() == null) {
        return null;
      }

      var iban = otherPartyAccount.getId().getIBAN();
      if (iban == null || iban.isBlank()) {
        return null;
      }

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

    var transactionType =
        creditOrDebit == CreditDebitCode.CRDT ? TransactionType.CREDIT : TransactionType.DEBIT;

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

    var externalId =
        Optional.ofNullable(entry.getNtryRef()).orElseGet(entry::getAcctSvcrRef);

    var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
    var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
    var endToEndId =
        Optional.ofNullable(transactionDetails.getRefs())
            .map(
                ee.tuleva.onboarding.banking.iso20022.camt053.TransactionReferences2::getEndToEndId)
            .orElse(null);

    var subFamilyCode = extractSubFamilyCode(entry);

    return new BankStatementEntry(
        counterPartyDetails,
        entryAmount,
        currencyCode,
        transactionType,
        remittanceInformation,
        externalId,
        endToEndId,
        subFamilyCode);
  }

  @Nullable
  private static String extractSubFamilyCode(
      ee.tuleva.onboarding.banking.iso20022.camt053.ReportEntry2 entry) {
    return Optional.ofNullable(entry.getBkTxCd())
        .map(ee.tuleva.onboarding.banking.iso20022.camt053.BankTransactionCodeStructure4::getDomn)
        .map(ee.tuleva.onboarding.banking.iso20022.camt053.BankTransactionCodeStructure5::getFmly)
        .map(
            ee.tuleva.onboarding.banking.iso20022.camt053.BankTransactionCodeStructure6
                ::getSubFmlyCd)
        .orElse(null);
  }

  static BankStatementEntry from(ReportEntry2 entry) {
    var counterPartyDetails = CounterPartyDetails.from(entry);
    var creditOrDebit = entry.getCdtDbtInd();
    var creditDebitCoefficient = creditOrDebit == CRDT ? BigDecimal.ONE : new BigDecimal("-1.0");
    var entryAmount = entry.getAmt().getValue().multiply(creditDebitCoefficient);

    var currencyCode = entry.getAmt().getCcy();

    var transactionType = creditOrDebit == CRDT ? TransactionType.CREDIT : TransactionType.DEBIT;

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

    var externalId =
        Optional.ofNullable(entry.getNtryRef()).orElseGet(entry::getAcctSvcrRef);

    var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
    var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
    var endToEndId =
        Optional.ofNullable(transactionDetails.getRefs())
            .map(
                ee.tuleva.onboarding.banking.iso20022.camt052.TransactionReferences2::getEndToEndId)
            .orElse(null);

    var subFamilyCode = extractSubFamilyCode(entry);

    return new BankStatementEntry(
        counterPartyDetails,
        entryAmount,
        currencyCode,
        transactionType,
        remittanceInformation,
        externalId,
        endToEndId,
        subFamilyCode);
  }

  @Nullable
  private static String extractSubFamilyCode(ReportEntry2 entry) {
    return Optional.ofNullable(entry.getBkTxCd())
        .map(ee.tuleva.onboarding.banking.iso20022.camt052.BankTransactionCodeStructure4::getDomn)
        .map(ee.tuleva.onboarding.banking.iso20022.camt052.BankTransactionCodeStructure5::getFmly)
        .map(
            ee.tuleva.onboarding.banking.iso20022.camt052.BankTransactionCodeStructure6
                ::getSubFmlyCd)
        .orElse(null);
  }
}
