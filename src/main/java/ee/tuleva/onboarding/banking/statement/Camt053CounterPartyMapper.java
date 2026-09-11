package ee.tuleva.onboarding.banking.statement;

import ee.tuleva.onboarding.banking.iso20022.camt053.CreditDebitCode;
import ee.tuleva.onboarding.banking.iso20022.camt053.GenericOrganisationIdentification1;
import ee.tuleva.onboarding.banking.iso20022.camt053.GenericPersonIdentification1;
import ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice;
import ee.tuleva.onboarding.banking.iso20022.camt053.PartyIdentification32;
import ee.tuleva.onboarding.banking.iso20022.camt053.ReportEntry2;
import ee.tuleva.onboarding.banking.iso20022.camt053.TransactionParty2;
import jakarta.annotation.Nullable;
import java.util.stream.Stream;

final class Camt053CounterPartyMapper {

  private Camt053CounterPartyMapper() {}

  @Nullable
  static BankStatementEntry.CounterPartyDetails from(ReportEntry2 entry) {
    var creditOrDebit = entry.getCdtDbtInd();
    var relatedParties = relatedParties(entry);
    if (relatedParties == null) {
      return null;
    }

    var otherParty = otherParty(relatedParties, creditOrDebit);
    if (otherParty == null) {
      return null;
    }

    var iban = otherPartyIban(relatedParties, creditOrDebit);
    if (iban == null) {
      return null;
    }

    var name = otherParty.getNm();
    var idCode = counterPartyIdCode(otherParty.getId(), name, iban);

    return new BankStatementEntry.CounterPartyDetails(name, iban, idCode);
  }

  @Nullable
  private static TransactionParty2 relatedParties(ReportEntry2 entry) {
    var entryDetails = Require.exactlyOne(entry.getNtryDtls(), "entry details");
    var transactionDetails = Require.exactlyOne(entryDetails.getTxDtls(), "transaction details");
    return transactionDetails.getRltdPties();
  }

  @Nullable
  private static PartyIdentification32 otherParty(
      TransactionParty2 relatedParties, CreditDebitCode creditOrDebit) {
    return creditOrDebit == CreditDebitCode.CRDT
        ? relatedParties.getDbtr()
        : relatedParties.getCdtr();
  }

  @Nullable
  private static String otherPartyIban(
      TransactionParty2 relatedParties, CreditDebitCode creditOrDebit) {
    var otherPartyAccount =
        creditOrDebit == CreditDebitCode.CRDT
            ? relatedParties.getDbtrAcct()
            : relatedParties.getCdtrAcct();
    if (otherPartyAccount == null || otherPartyAccount.getId() == null) {
      return null;
    }
    var iban = otherPartyAccount.getId().getIBAN();
    return (iban == null || iban.isBlank()) ? null : iban;
  }

  @Nullable
  private static String counterPartyIdCode(
      @Nullable Party6Choice partyId, String name, String iban) {
    var prvtId = partyId == null ? null : partyId.getPrvtId();
    var orgId = partyId == null ? null : partyId.getOrgId();

    if (prvtId != null && orgId != null) {
      throw new BankStatementParseException(
          "Counterparty has both OrgId and PrvtId: name=" + name + ", iban=" + iban);
    }

    var idCodes =
        Stream.concat(
                prvtId == null
                    ? Stream.<String>empty()
                    : prvtId.getOthr().stream().map(GenericPersonIdentification1::getId),
                orgId == null
                    ? Stream.<String>empty()
                    : orgId.getOthr().stream().map(GenericOrganisationIdentification1::getId))
            .filter(id -> id != null && !id.isBlank())
            .toList();
    return Require.atMostOne(idCodes, "counterparty ID code");
  }
}
