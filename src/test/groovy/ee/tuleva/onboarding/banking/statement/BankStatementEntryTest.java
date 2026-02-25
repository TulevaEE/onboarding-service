package ee.tuleva.onboarding.banking.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.banking.iso20022.camt052.*;
import ee.tuleva.onboarding.banking.iso20022.camt053.DateAndDateTimeChoice;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import javax.xml.datatype.DatatypeFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BankStatementEntryTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final Instant RECEIVED_BEFORE = Instant.parse("2026-01-31T21:59:59.999999Z");

  @Test
  void extractReceivedBefore_hasMicrosecondPrecision() throws Exception {
    var bookingDate = new DateAndDateTimeChoice();
    bookingDate.setDt(DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-01-31"));

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
}
