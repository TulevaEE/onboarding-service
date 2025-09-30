package ee.tuleva.onboarding.swedbank.statement;

import static ee.swedbank.gateway.iso.response.report.CreditDebitCode.CRDT;

import ee.swedbank.gateway.iso.response.report.Party6Choice;
import ee.swedbank.gateway.iso.response.report.ReportEntry2;
import ee.swedbank.gateway.iso.response.statement.CreditDebitCode;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public record BankStatementEntry(CounterPartyDetails details, BigDecimal amount) {

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
              .flatMap(
                  genericPersonIdentification1 ->
                      Optional.of(genericPersonIdentification1.getId()));

      var otherPartyAccount =
          creditOrDebit == CRDT ? relatedParties.getDbtrAcct() : relatedParties.getCdtrAcct();

      var iban = otherPartyAccount.getId().getIBAN();

      return new CounterPartyDetails(name, iban, personalIdCode.orElse(null));
    }

    public static CounterPartyDetails from(
        ee.swedbank.gateway.iso.response.statement.ReportEntry2 entry) {
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
              .flatMap(
                  genericPersonIdentification1 ->
                      Optional.of(genericPersonIdentification1.getId()));

      var otherPartyAccount =
          creditOrDebit == CreditDebitCode.CRDT
              ? relatedParties.getDbtrAcct()
              : relatedParties.getCdtrAcct();

      var iban = otherPartyAccount.getId().getIBAN();

      return new CounterPartyDetails(name, iban, personalIdCode.orElse(null));
    }
  }

  public static BankStatementEntry from(
      ee.swedbank.gateway.iso.response.statement.ReportEntry2 entry) {
    var counterPartyDetails = CounterPartyDetails.from(entry);
    var creditOrDebit = entry.getCdtDbtInd();
    var creditDebitCoefficient =
        creditOrDebit == CreditDebitCode.CRDT ? BigDecimal.ONE : new BigDecimal("-1.0");
    var entryAmount = entry.getAmt().getValue().multiply(creditDebitCoefficient);

    return new BankStatementEntry(counterPartyDetails, entryAmount);
  }

  public static BankStatementEntry from(ReportEntry2 entry) {
    var counterPartyDetails = CounterPartyDetails.from(entry);
    var creditOrDebit = entry.getCdtDbtInd();
    var creditDebitCoefficient = creditOrDebit == CRDT ? BigDecimal.ONE : new BigDecimal("-1.0");
    var entryAmount = entry.getAmt().getValue().multiply(creditDebitCoefficient);

    return new BankStatementEntry(counterPartyDetails, entryAmount);
  }
}
