package ee.tuleva.onboarding.banking.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.banking.iso20022.camt052.*;
import ee.tuleva.onboarding.banking.iso20022.camt053.DateAndDateTimeChoice;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BankStatementEntryTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final Instant RECEIVED_BEFORE = Instant.parse("2026-01-31T21:59:59.999999Z");

  @Test
  void extractReceivedBefore_hasMicrosecondPrecision() {
    var bookingDate = new DateAndDateTimeChoice();
    bookingDate.setDt(LocalDate.of(2026, 1, 31));

    var result = BankStatementEntry.extractReceivedBefore(bookingDate, TALLINN);

    assertThat(result).isEqualTo(RECEIVED_BEFORE);
  }

  @Test
  void extractReceivedBefore_returnsNullForNullBookingDate() {
    var result = BankStatementEntry.extractReceivedBefore(null, TALLINN);

    assertThat(result).isNull();
  }

  @Nested
  class RemittanceInformationExtraction {

    @Test
    void extractsFromUnstructuredRemittanceInfo() {
      var entry = entryWithRemittanceInfo(unstructuredRemittance("38501010002"));

      var result = BankStatementEntry.from(entry, RECEIVED_BEFORE);

      assertThat(result.remittanceInformation()).isEqualTo("38501010002");
    }

    @Test
    void extractsFromStructuredCreditorReference() {
      var entry = entryWithRemittanceInfo(structuredCreditorRefRemittance("38501010002"));

      var result = BankStatementEntry.from(entry, RECEIVED_BEFORE);

      assertThat(result.remittanceInformation()).isEqualTo("38501010002");
    }

    @Test
    void extractsFromStructuredAdditionalRemittanceInfo() {
      var entry = entryWithRemittanceInfo(structuredAdditionalRemittance("38501010002"));

      var result = BankStatementEntry.from(entry, RECEIVED_BEFORE);

      assertThat(result.remittanceInformation()).isEqualTo("38501010002");
    }

    @Test
    void prefersUnstructuredOverStructured() {
      var remittanceInfo = new RemittanceInformation5();
      remittanceInfo.getUstrd().add("from-ustrd");
      var strd = new StructuredRemittanceInformation7();
      var cdtrRefInf = new CreditorReferenceInformation2();
      cdtrRefInf.setRef("from-strd");
      strd.setCdtrRefInf(cdtrRefInf);
      remittanceInfo.getStrd().add(strd);

      var entry = entryWithRemittanceInfo(remittanceInfo);

      var result = BankStatementEntry.from(entry, RECEIVED_BEFORE);

      assertThat(result.remittanceInformation()).isEqualTo("from-ustrd");
    }

    @Test
    void throwsWhenNoRemittanceInfo() {
      var entry = entryWithRemittanceInfo(null);

      assertThatThrownBy(() -> BankStatementEntry.from(entry, RECEIVED_BEFORE))
          .isInstanceOf(BankStatementParseException.class);
    }

    @Test
    void throwsWhenStructuredHasNoRefOrAdditionalInfo() {
      var remittanceInfo = new RemittanceInformation5();
      remittanceInfo.getStrd().add(new StructuredRemittanceInformation7());

      var entry = entryWithRemittanceInfo(remittanceInfo);

      assertThatThrownBy(() -> BankStatementEntry.from(entry, RECEIVED_BEFORE))
          .isInstanceOf(BankStatementParseException.class);
    }

    private ReportEntry2 entryWithRemittanceInfo(RemittanceInformation5 remittanceInfo) {
      var amount = new ActiveOrHistoricCurrencyAndAmount();
      amount.setValue(new BigDecimal("100.00"));
      amount.setCcy("EUR");

      var transaction = new EntryTransaction2();
      transaction.setRmtInf(remittanceInfo);

      var entryDetails = new EntryDetails1();
      entryDetails.getTxDtls().add(transaction);

      var entry = new ReportEntry2();
      entry.setAmt(amount);
      entry.setCdtDbtInd(CreditDebitCode.CRDT);
      entry.setNtryRef("EXT-001");
      entry.getNtryDtls().add(entryDetails);
      return entry;
    }

    private RemittanceInformation5 unstructuredRemittance(String value) {
      var remittanceInfo = new RemittanceInformation5();
      remittanceInfo.getUstrd().add(value);
      return remittanceInfo;
    }

    private RemittanceInformation5 structuredCreditorRefRemittance(String ref) {
      var cdtrRefInf = new CreditorReferenceInformation2();
      cdtrRefInf.setRef(ref);
      var strd = new StructuredRemittanceInformation7();
      strd.setCdtrRefInf(cdtrRefInf);
      var remittanceInfo = new RemittanceInformation5();
      remittanceInfo.getStrd().add(strd);
      return remittanceInfo;
    }

    private RemittanceInformation5 structuredAdditionalRemittance(String value) {
      var strd = new StructuredRemittanceInformation7();
      strd.getAddtlRmtInf().add(value);
      var remittanceInfo = new RemittanceInformation5();
      remittanceInfo.getStrd().add(strd);
      return remittanceInfo;
    }
  }

  @Nested
  class CounterPartyExtraction {

    @Test
    void extractsCompanyIdCodeFromOrgId() {
      var entry = creditEntryWithCounterparty(orgId("10060701"), "Acme OÜ", "EE157700771001802057");

      var result = BankStatementEntry.from(entry, RECEIVED_BEFORE);

      assertThat(result.details().getIdCode()).contains("10060701");
    }

    @Test
    void extractsPersonIdCodeFromPrvtId() {
      var entry =
          creditEntryWithCounterparty(prvtId("39910273027"), "Jüri Tamm", "EE157700771001802057");

      var result = BankStatementEntry.from(entry, RECEIVED_BEFORE);

      assertThat(result.details().getIdCode()).contains("39910273027");
    }

    @Test
    void throwsWhenBothOrgIdAndPrvtIdPresent() {
      var entry =
          creditEntryWithCounterparty(
              bothIds("10060701", "39910273027"), "Ambiguous", "EE157700771001802057");

      assertThatThrownBy(() -> BankStatementEntry.from(entry, RECEIVED_BEFORE))
          .isInstanceOf(BankStatementParseException.class);
    }

    private ReportEntry2 creditEntryWithCounterparty(
        Party6Choice counterpartyId, String name, String iban) {
      var amount = new ActiveOrHistoricCurrencyAndAmount();
      amount.setValue(new BigDecimal("100.00"));
      amount.setCcy("EUR");

      var debtor = new PartyIdentification32();
      debtor.setNm(name);
      debtor.setId(counterpartyId);

      var debtorAccountId = new AccountIdentification4Choice();
      debtorAccountId.setIBAN(iban);
      var debtorAccount = new CashAccount16();
      debtorAccount.setId(debtorAccountId);

      var relatedParties = new TransactionParty2();
      relatedParties.setDbtr(debtor);
      relatedParties.setDbtrAcct(debtorAccount);

      var remittanceInfo = new RemittanceInformation5();
      remittanceInfo.getUstrd().add("Test payment");

      var transaction = new EntryTransaction2();
      transaction.setRmtInf(remittanceInfo);
      transaction.setRltdPties(relatedParties);

      var entryDetails = new EntryDetails1();
      entryDetails.getTxDtls().add(transaction);

      var entry = new ReportEntry2();
      entry.setAmt(amount);
      entry.setCdtDbtInd(CreditDebitCode.CRDT);
      entry.setNtryRef("EXT-001");
      entry.getNtryDtls().add(entryDetails);
      return entry;
    }

    private Party6Choice orgId(String code) {
      var generic = new GenericOrganisationIdentification1();
      generic.setId(code);
      var organisationId = new OrganisationIdentification4();
      organisationId.getOthr().add(generic);
      var party = new Party6Choice();
      party.setOrgId(organisationId);
      return party;
    }

    private Party6Choice prvtId(String code) {
      var party = new Party6Choice();
      party.setPrvtId(personIdentification(code));
      return party;
    }

    private Party6Choice bothIds(String orgCode, String personCode) {
      var party = orgId(orgCode);
      party.setPrvtId(personIdentification(personCode));
      return party;
    }

    private PersonIdentification5 personIdentification(String code) {
      var generic = new GenericPersonIdentification1();
      generic.setId(code);
      var personId = new PersonIdentification5();
      personId.getOthr().add(generic);
      return personId;
    }
  }

  @Nested
  class Camt053CounterPartyExtraction {

    @Test
    void extractsCompanyIdCodeFromOrgId() {
      var entry = creditEntryWithCounterparty(orgId("10060701"), "Acme OÜ", "EE157700771001802057");

      var result = BankStatementEntry.from(entry, TALLINN);

      assertThat(result.details().getIdCode()).contains("10060701");
    }

    @Test
    void throwsWhenBothOrgIdAndPrvtIdPresent() {
      var entry =
          creditEntryWithCounterparty(
              bothIds("10060701", "39910273027"), "Ambiguous", "EE157700771001802057");

      assertThatThrownBy(() -> BankStatementEntry.from(entry, TALLINN))
          .isInstanceOf(BankStatementParseException.class);
    }

    private ee.tuleva.onboarding.banking.iso20022.camt053.ReportEntry2 creditEntryWithCounterparty(
        ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice counterpartyId,
        String name,
        String iban) {
      var amount =
          new ee.tuleva.onboarding.banking.iso20022.camt053.ActiveOrHistoricCurrencyAndAmount();
      amount.setValue(new BigDecimal("100.00"));
      amount.setCcy("EUR");

      var debtor = new ee.tuleva.onboarding.banking.iso20022.camt053.PartyIdentification32();
      debtor.setNm(name);
      debtor.setId(counterpartyId);

      var debtorAccountId =
          new ee.tuleva.onboarding.banking.iso20022.camt053.AccountIdentification4Choice();
      debtorAccountId.setIBAN(iban);
      var debtorAccount = new ee.tuleva.onboarding.banking.iso20022.camt053.CashAccount16();
      debtorAccount.setId(debtorAccountId);

      var relatedParties = new ee.tuleva.onboarding.banking.iso20022.camt053.TransactionParty2();
      relatedParties.setDbtr(debtor);
      relatedParties.setDbtrAcct(debtorAccount);

      var remittanceInfo =
          new ee.tuleva.onboarding.banking.iso20022.camt053.RemittanceInformation5();
      remittanceInfo.getUstrd().add("Test payment");

      var transaction = new ee.tuleva.onboarding.banking.iso20022.camt053.EntryTransaction2();
      transaction.setRmtInf(remittanceInfo);
      transaction.setRltdPties(relatedParties);

      var entryDetails = new ee.tuleva.onboarding.banking.iso20022.camt053.EntryDetails1();
      entryDetails.getTxDtls().add(transaction);

      var entry = new ee.tuleva.onboarding.banking.iso20022.camt053.ReportEntry2();
      entry.setAmt(amount);
      entry.setCdtDbtInd(ee.tuleva.onboarding.banking.iso20022.camt053.CreditDebitCode.CRDT);
      entry.setNtryRef("EXT-001");
      entry.getNtryDtls().add(entryDetails);
      return entry;
    }

    private ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice orgId(String code) {
      var generic =
          new ee.tuleva.onboarding.banking.iso20022.camt053.GenericOrganisationIdentification1();
      generic.setId(code);
      var organisationId =
          new ee.tuleva.onboarding.banking.iso20022.camt053.OrganisationIdentification4();
      organisationId.getOthr().add(generic);
      var party = new ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice();
      party.setOrgId(organisationId);
      return party;
    }

    private ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice bothIds(
        String orgCode, String personCode) {
      var party = orgId(orgCode);
      var generic =
          new ee.tuleva.onboarding.banking.iso20022.camt053.GenericPersonIdentification1();
      generic.setId(personCode);
      var personId = new ee.tuleva.onboarding.banking.iso20022.camt053.PersonIdentification5();
      personId.getOthr().add(generic);
      party.setPrvtId(personId);
      return party;
    }
  }
}
